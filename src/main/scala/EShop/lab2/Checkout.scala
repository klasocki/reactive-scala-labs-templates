package EShop.lab2

import EShop.lab2.Checkout._
import EShop.lab3.Payment
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ConfirmPaymentReceived              extends Command

  sealed trait Event
  case object CheckOutClosed                   extends Event
  case class PaymentStarted(payment: ActorRef) extends Event

  def props(cart: ActorRef) = Props(new Checkout(cart))
}

class Checkout(
  cartActor: ActorRef
) extends Actor {

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  val checkoutTimerDuration = 1 seconds
  val paymentTimerDuration  = 1 seconds

  def receive: Receive = LoggingReceive {
    case StartCheckout =>
      context become selectingDelivery(scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout))
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case ExpireCheckout | CancelCheckout =>
      cartActor ! CartActor.ConfirmCheckoutCancelled
      context become cancelled
    case SelectDeliveryMethod(method) =>
      context become selectingPaymentMethod(timer)
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case ExpireCheckout | CancelCheckout =>
      cartActor ! CartActor.ConfirmCheckoutCancelled
      context become cancelled
    case SelectPayment(method) =>
      timer.cancel()
      val payment = context.system.actorOf(Props(new Payment(method, sender, self)))
      sender ! PaymentStarted(payment)
      context become processingPayment(scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment))
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case ExpirePayment | CancelCheckout =>
      cartActor ! CartActor.ConfirmCheckoutCancelled
      context become cancelled
    case ConfirmPaymentReceived =>
      cartActor ! CartActor.ConfirmCheckoutClosed
      context become closed
  }

  def cancelled: Receive = LoggingReceive {
    case _ => context stop self
  }

  def closed: Receive = LoggingReceive {
    case _ => context stop self
  }

}
