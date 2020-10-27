package EShop.lab2

import akka.actor.{ActorSystem, Props}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main extends App {
  val system = ActorSystem("System")
  val cart = system.actorOf(Props[CartActor])

  cart ! CartActor.AddItem("Lodowka")
  cart ! CartActor.AddItem("Fridge")
  cart ! CartActor.RemoveItem("Fridge")
  cart ! CartActor.RemoveItem("Lod")
  cart ! CartActor.StartCheckout

  val checkout = system.actorOf(Props[Checkout])
  checkout ! Checkout.StartCheckout
  checkout ! Checkout.SelectDeliveryMethod("post")
  checkout ! Checkout.SelectPayment("card")
  checkout ! Checkout.ConfirmPaymentReceived

  Await.result(system.whenTerminated, Duration.Inf)
}
