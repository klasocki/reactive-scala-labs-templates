package EShop.lab3

import EShop.lab2.{CartActor, Checkout}
import EShop.lab3.OrderManager._
import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive

object OrderManager {
  sealed trait Command
  case class AddItem(id: String) extends Command
  case class RemoveItem(id: String) extends Command
  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String) extends Command
  case object Buy extends Command
  case object Pay extends Command
  case class ConfirmCheckoutStarted(checkoutRef: ActorRef) extends Command
  case class ConfirmPaymentStarted(paymentRef: ActorRef) extends Command
  case object ConfirmPaymentReceived extends Command

  sealed trait Ack
  case object Done extends Ack //trivial ACK
}

class OrderManager extends Actor {

  override def receive: Receive = uninitialized

  def uninitialized: Receive = LoggingReceive {
    case AddItem(x) =>
      val cart = context.system.actorOf(Props[CartActor])
      cart ! CartActor.AddItem(x)
      context become open(cart)
      sender ! Done
  }

  def open(cartActor: ActorRef): Receive = LoggingReceive {
    case AddItem(id) =>
      cartActor ! CartActor.AddItem(id)
      sender ! Done
    case RemoveItem(id) =>
      cartActor ! CartActor.RemoveItem(id)
      sender ! Done
    case Buy =>
      context become waitingForCheckout(senderRef = sender)
      cartActor ! CartActor.StartCheckout
  }

  def waitingForCheckout(senderRef: ActorRef): Receive = LoggingReceive {
    case CartActor.CheckoutStarted(checkoutRef) =>
      context become inCheckout(checkoutRef)
      senderRef ! Done
  }

  def inCheckout(checkoutActorRef: ActorRef): Receive = LoggingReceive {
    case SelectDeliveryAndPaymentMethod(delivery, payment) =>
      checkoutActorRef ! Checkout.SelectDeliveryMethod(delivery)
      checkoutActorRef ! Checkout.SelectPayment(payment)
      context become waitingForPayment(sender)
  }

  def waitingForPayment(senderRef: ActorRef): Receive = LoggingReceive {
    case Checkout.PaymentStarted(payment) =>
      context become inPayment(payment)
      senderRef ! Done
  }

  def inPayment(paymentActorRef: ActorRef): Receive = LoggingReceive {
    case Pay =>
      context become waitingForPaymentComplete(sender)
      paymentActorRef ! Payment.DoPayment
  }

  def waitingForPaymentComplete(senderRef: ActorRef): Receive = LoggingReceive {
    case ConfirmPaymentReceived =>
      context become finished
      senderRef ! Done
  }

  def finished: Receive = {
    case _ => sender ! "order manager finished job"
  }
}
