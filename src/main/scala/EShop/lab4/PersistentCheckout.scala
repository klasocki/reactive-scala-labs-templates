package EShop.lab4

import EShop.lab2.CartActor
import EShop.lab3.Payment
import akka.actor.{ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}
import akka.persistence.PersistentActor

import scala.util.Random
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object PersistentCheckout {

  def props(cartActor: ActorRef, persistenceId: String): Props =
    Props(new PersistentCheckout(cartActor, persistenceId))
}

class PersistentCheckout(
  cartActor: ActorRef,
  val persistenceId: String
) extends PersistentActor {

  import EShop.lab2.Checkout._
  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)
  val timerDuration: FiniteDuration = 1.seconds

  private def updateState(event: Event, maybeTimer: Option[Cancellable] = None): Unit = {
    lazy val newTimer = maybeTimer getOrElse scheduleTimer
    event match {
      case CheckoutStarted                => context become selectingDelivery(newTimer)
      case DeliveryMethodSelected(method) => context become selectingPaymentMethod(newTimer)
      case CheckOutClosed                 => context become closed
      case CheckoutCancelled              => context become cancelled
      case PaymentStarted(payment)        => context become processingPayment(newTimer)

    }
  }
  private def scheduleTimer(): Cancellable = context.system.scheduler.scheduleOnce(timerDuration, self, ExpireCheckout)

  def receiveCommand: Receive = LoggingReceive {
    case StartCheckout =>
      persist(CheckoutStarted) {
        event =>
          updateState(event)
      }
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case ExpireCheckout | CancelCheckout =>
      persist(CheckoutCancelled) {
        event =>
          updateState(event)
          cartActor ! CartActor.ConfirmCheckoutCancelled
      }
    case SelectDeliveryMethod(method) =>
      persist(DeliveryMethodSelected(method)) {
        event =>
          updateState(event, Option(timer))
      }
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case ExpireCheckout | CancelCheckout =>
      persist(CheckoutCancelled) {
        event =>
          updateState(event)
          cartActor ! CartActor.ConfirmCheckoutCancelled
      }
    case SelectPayment(method) =>
      val payment = context.system.actorOf(Props(new Payment(method, sender, self)))
      persist(PaymentStarted(payment)) {
        event =>
          timer.cancel()
          sender ! PaymentStarted(payment)
          updateState(event)
      }
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case ExpirePayment | CancelCheckout =>
      persist(CheckoutCancelled) {
        event =>
          updateState(event)
          cartActor ! CartActor.ConfirmCheckoutCancelled
      }
    case ConfirmPaymentReceived =>
      persist(CheckOutClosed) {
        event =>
          updateState(event)
          cartActor ! CartActor.ConfirmCheckoutClosed
      }
  }

  def cancelled: Receive = LoggingReceive {
    case _ => context stop self
  }

  def closed: Receive = LoggingReceive {
    case _ => context stop self
  }
  override def receiveRecover: Receive =  LoggingReceive {
    case x: Event =>
      updateState(x)
  }
}
