package EShop.lab4

import EShop.lab2.{CartActor, Checkout}
import EShop.lab2.Main.cart
import EShop.lab2.CartActor.GetItems
import EShop.lab3.OrderManager
import EShop.lab4.Main.system
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.LoggingReceive

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Random

class MainActor extends Actor {
  val cartId: String = "2"

  override def receive: Receive = LoggingReceive {
    case "init" =>
      val cart = system.actorOf(Props(new PersistentCartActor(cartId)))
      cart ! CartActor.AddItem("Lodowka")
      cart ! CartActor.AddItem("Fridge")
      cart ! CartActor.RemoveItem("Fridge")
      cart ! CartActor.RemoveItem("Lod")
      cart ! CartActor.StartCheckout

    case OrderManager.ConfirmCheckoutStarted(checkoutRef) =>
      context.system.terminate()

    case "resume" =>
      val cart = system.actorOf(Props(new PersistentCartActor(cartId)))
      val checkout = system.actorOf(Props(new PersistentCheckout(cart, cartId + "ch")))

      checkout ! Checkout.SelectDeliveryMethod("method")
      cart ! GetItems
      Thread.sleep(1500)
      checkout ! Checkout.SelectPayment("payment")
      checkout ! Checkout.ConfirmPaymentReceived
      checkout ! ""

      Thread.sleep(1500)
      cart ! GetItems

    case x: Seq[String] => println(s"Objects in cart: $x")
  }

}

object Main extends App{
  var system = ActorSystem("System")
  var main = system.actorOf(Props(new MainActor))
  main ! "init"

  Await.result(system.whenTerminated, Duration.Inf)

  system = ActorSystem("System")
  main = system.actorOf(Props(new MainActor))
  main ! "resume"

}
