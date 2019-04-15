package main.scala.stats

import main.scala.mix.EventInformation

/**
  * parsed editing outcome events from a stats file
  */
case class Barcode(events: Array[String], count: Int, proportion: Double, sample: String, name: String) {
  val builder = new StringBuilder()

  // convert the input name to one that's 9 letters long followed by a space, a constraint of MIX
  val mixName = name.slice(0,9) + (0 until (10 - math.min(10, name.length))).map{ i => " "}.mkString("")

  /**
    *
    * @param index
    * @return
    */
  def toMixString(): Tuple2[String,Boolean] = {
    var isWT = true
    val ret = mixName + (1 until EventInformation.numberOfColumns()).map{ ind => {

      // here we need to check if we have an UNKNOWN or CONFLICT in the positions that this event occupies
      val eventObj = EventInformation.columnToEvent(ind)
      if (events contains eventObj.eventString) {
        isWT = false
        "1"

      } else {
        if (eventObj.positions.map{pos => if (events(pos) == "UNKNOWN" || events(pos) == "CONFLICT") 1 else 0}.sum > 0) {
          isWT = false
          "?"
        } else {
          "0"
        }
      }
    }}.mkString("")
    return (ret,isWT)
  }

  def prettyString(): String = {
    events.mkString("-") + " " + count + " " + proportion + sample + " " + name
  }
}