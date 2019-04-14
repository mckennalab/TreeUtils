package main.scala.stats

/**
  * parsed editing outcome events from a stats file
  */
case class Event(events: Array[String], eventNumbers: Array[Int], count: Int, proportion: Double, sample: String, name: String) {
  val builder = new StringBuilder()

  // convert the input name to one that's 9 letters long followed by a space, a constraint of MIX
  val mixName = name.slice(0,9) + (0 until (10 - math.min(10, name.length))).map{ i => " "}.mkString("")

  /**
    *
    * @param index
    * @return
    */
  def toMixString(index: Int): Tuple2[String,Boolean] = {
    var isWT = true
    val ret = mixName + (1 until index).map{ ind => {
      if (eventNumbers contains ind) {
        isWT = false
        "1"
      } else {
        "0"
      }
    }}.mkString("")
    return (ret,isWT)
  }

  def prettyString(): String = {
    events.mkString("-") + " " + eventNumbers.mkString("-") + " " + count + " " + proportion + sample + " " + name
  }
}