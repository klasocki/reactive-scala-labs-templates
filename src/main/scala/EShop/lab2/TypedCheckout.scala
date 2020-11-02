package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object TypedCheckout {

  sealed trait Data

  case object Uninitialized extends Data

  case class SelectingDeliveryStarted(timer: Cancellable) extends Data

  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command

  case object StartCheckout extends Command

  case class SelectDeliveryMethod(method: String) extends Command

  case object CancelCheckout extends Command

  case object ExpireCheckout extends Command

  case class SelectPayment(payment: String) extends Command

  case object ExpirePayment extends Command

  case object ConfirmPaymentReceived extends Command

  sealed trait Event

  case object CheckOutClosed extends Event

  case class PaymentStarted(payment: ActorRef[Any]) extends Event

}

class TypedCheckout(
                     cartActor: ActorRef[TypedCartActor.Command]
                   ) {

  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration = 1 seconds

  private def scheduleCheckoutTimer(context: ActorContext[TypedCheckout.Command]): Cancellable =
    context.system.scheduler.scheduleOnce(
      checkoutTimerDuration,
      () => context.self ! ExpireCheckout
    )

  private def schedulePaymentTimer(context: ActorContext[TypedCheckout.Command]): Cancellable =
    context.system.scheduler.scheduleOnce(
      paymentTimerDuration,
      () => context.self ! ExpirePayment
    )

  def start: Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) => msg match {
      case StartCheckout =>
        selectingDelivery(scheduleCheckoutTimer(context))
      case _ =>
        Behaviors.same
    })

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receiveMessage {
    case ExpireCheckout | CancelCheckout =>
      cancelled
    case SelectDeliveryMethod(m) =>
      selectingPaymentMethod(timer)
    case _ =>
      Behaviors.same
  }

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) => msg match {
      case ExpireCheckout | CancelCheckout =>
        cancelled
      case SelectPayment(p) =>
        processingPayment(schedulePaymentTimer(context))
      case _ =>
        Behaviors.same
    }
  )

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receiveMessage {
    case ExpirePayment | CancelCheckout =>
      cancelled
    case ConfirmPaymentReceived =>
      closed
    case _ =>
      Behaviors.same
  }

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.stopped

  def closed: Behavior[TypedCheckout.Command] = Behaviors.stopped

}
