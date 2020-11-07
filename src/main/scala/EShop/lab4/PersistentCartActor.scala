package EShop.lab4

import EShop.lab2.{Cart, Checkout}
import EShop.lab3.OrderManager
import akka.actor.{Cancellable, Props}
import akka.event.{Logging, LoggingReceive}
import akka.persistence.PersistentActor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object PersistentCartActor {

  def props(persistenceId: String): Props = Props(new PersistentCartActor(persistenceId))
}

class PersistentCartActor(
                           val persistenceId: String
                         ) extends PersistentActor {

  import EShop.lab2.CartActor._

  private val log = Logging(context.system, this)
  val cartTimerDuration: FiniteDuration = 5.seconds

  override def receiveCommand: Receive = empty

  private def updateState(event: Event, timer: Option[Cancellable] = None): Unit = {
    lazy val newTimer = timer getOrElse scheduleTimer
    event match {
      case CartExpired | CheckoutClosed => context become empty
      case CheckoutCancelled(cart) => context become nonEmpty(cart, newTimer)
      case ItemAdded(item, cart) => context become nonEmpty(cart.addItem(item), newTimer)
      case CartEmptied => context become empty
      case CheckoutStarted(checkoutRef, cart) => context become inCheckout(cart)
      case ItemRemoved(item, cart) => if (cart.size == 1)
        context become empty
      else
        context become nonEmpty(cart.removeItem(item), newTimer)
    }
  }

  private def scheduleTimer(): Cancellable = context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)


  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      persist(ItemAdded(itemId = item, cart = Cart.empty)) {
        event => updateState(event)
      }
    case GetItems => sender ! Seq.empty[String]
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case GetItems => sender ! cart.items
    case AddItem(item) =>
      persist(ItemAdded(itemId = item, cart = cart)) {
        event => updateState(event, Option(timer))
      }
    case ExpireCart =>
      persist(CartExpired) {
        event => updateState(event)
      }
    case StartCheckout =>
      val checkout = context.system.actorOf(Props(new PersistentCheckout(self, this.persistenceId + "ch")))
      persist(CheckoutStarted(checkoutRef = checkout, cart = cart)) {
        event =>
          timer.cancel()
          updateState(event)
          checkout ! Checkout.StartCheckout
          sender ! OrderManager.ConfirmCheckoutStarted(checkout)
      }

    case RemoveItem(item) if cart.contains(item) =>
      persist(ItemRemoved(itemId = item, cart = cart)) {
        event => updateState(event, Option(timer))
      }
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case ConfirmCheckoutCancelled =>
      persist(CheckoutCancelled(cart = cart)) {
        event => updateState(event)
      }
    case ConfirmCheckoutClosed =>
      context stop self
  }

  override def receiveRecover: Receive = LoggingReceive {
    case x: Event =>
      updateState(x)
  }
}
