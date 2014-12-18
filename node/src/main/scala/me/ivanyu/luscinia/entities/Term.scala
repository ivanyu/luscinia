package me.ivanyu.luscinia.entities

/**
 * Operation term
 * @param t integer value of the term
 */
case class Term(t: Int) extends AnyVal with Ordered[Term] {
  def next: Term = Term(t + 1)
  def prev: Term = Term(t - 1)

  override def compare(that: Term): Int = this.t - that.t
}

object Term {
  val start = Term(0)
}