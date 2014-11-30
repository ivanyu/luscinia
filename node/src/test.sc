object A {

  trait Ololo {
    val x: Int
  }

  object Ololo {
    implicit val ololo = new Ololo {
      val x = 12
    }
  }

  val x = implicitly[Ololo].x
  println(x)
}

