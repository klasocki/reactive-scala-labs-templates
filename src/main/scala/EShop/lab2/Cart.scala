package EShop.lab2

import EShop.lab2.Cart.empty


case class Cart(items: Seq[Any]) {
  def contains(item: Any): Boolean = items contains item
  def addItem(item: Any): Cart     = Cart(items :+ item)
  def removeItem(item: Any): Cart  = if (items.size == 1) empty else Cart(items filter (it => it != item))
  def size: Int                    = items.size
}

object Cart {
  def empty: Cart = Cart(Seq.empty)
}
