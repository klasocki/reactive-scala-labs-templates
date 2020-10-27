package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props, Timers}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)        extends Command
  case class RemoveItem(item: Any)     extends Command
  case object ExpireCart               extends Command
  case object StartCheckout            extends Command
  case object ConfirmCheckoutCancelled extends Command
  case object ConfirmCheckoutClosed    extends Command
  case object GetItems                 extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef, cart: Cart) extends Event
  case class ItemAdded(itemId: Any, cart: Cart)                 extends Event
  case class ItemRemoved(itemId: Any, cart: Cart)               extends Event
  case object CartEmptied                                       extends Event
  case object CartExpired                                       extends Event
  case object CheckoutClosed                                    extends Event
  case class CheckoutCancelled(cart: Cart)                      extends Event

  def props: Props = Props(new CartActor())
}

class CartActor extends Actor{
  import CartActor._

  private val log       = Logging(context.system, this)
  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer(): Cancellable = context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  def receive: Receive = LoggingReceive {
    case AddItem(item) =>
      context become nonEmpty(Cart.empty.addItem(item), scheduleTimer())
  }

  def empty: Receive = receive

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item) =>
      context become nonEmpty(cart.addItem(item), timer)
    case ExpireCart =>
      context become empty
    case StartCheckout =>
      context become inCheckout(cart)
    case RemoveItem(item) if cart.contains(item) =>
      if (cart.size == 1) {
        context become empty
      } else {
        val newCart = cart.removeItem(item)
        context become nonEmpty(newCart, timer)
      }
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case ConfirmCheckoutCancelled =>
      context become nonEmpty(cart, scheduleTimer())
    case ConfirmCheckoutClosed =>
      context become empty
  }

}
