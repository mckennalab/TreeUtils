package main.scala.mix

import scala.collection.mutable.{HashMap}
import scala.collection.immutable.Set

/**
  * The class encodes the relationship between an event string and it's column number
  *
  */
class EventInformation(eventStr: String, colNumber: Int, pos: Set[Int]) {
  val eventString = eventStr
  val columnNumber = colNumber
  var positions = pos
  var count = 1
}

/**
  * a factory that records event to MIX column relationships
  */
object EventInformation {

  val eventToEncoding  = new HashMap[String, EventInformation]()
  val columnToEvent    = new HashMap[Int, EventInformation]()
  val eventToPositions = new HashMap[String, Set[Int]]()
  var orderedEvents    = List[EventInformation]()

  // start at one
  var currentColumn = 1

  var specialEncodings = Map("NONE" -> 0, "CONFLICT" -> -1, "UNKNOWN" -> -2)

  specialEncodings.foreach{case(event, index) => {
    eventToEncoding(event) = new EventInformation(event, index, Set[Int]())
  }}

  def event(event: String): EventInformation = eventToEncoding(event)

  def column(col: Int): EventInformation = columnToEvent(col)

  def allEvents(): Set[String] = eventToEncoding.keySet.map{t => t}.toSet

  def numberOfColumns(): Int = currentColumn - 1

  /**
    * encode an event, allocating a new column id if we haven't seen the event yet
    * @param eventString the string
    * @param positions the position within the target array
    * @return an event information object
    */
  def encodeEvent(eventString: String, positions: Set[Int], count: Int): EventInformation = {

    if (eventToEncoding contains eventString) {
      // make sure we have this position recorded, events can have multiple positions
      eventToPositions(eventString) = eventToPositions.getOrElse(eventString, Set[Int]()) ++ positions
      eventToEncoding(eventString).count = eventToEncoding(eventString).count + count
      eventToEncoding(eventString).positions = eventToEncoding(eventString).positions ++ positions
      eventToEncoding(eventString)

    } else {
      val ret = new EventInformation(eventString, currentColumn, positions)
      ret.count = count
      orderedEvents :+= ret

      // update our internal event tracking
      eventToEncoding(eventString) = ret
      columnToEvent(currentColumn) = ret
      eventToPositions(eventString) = positions
      currentColumn += 1
      ret
    }
  }

  /**
    * add a series of events parsed from an input file
    * @param events the events array
    */
  def addEvents(events: Array[String], count: Int): Array[Int] = {
    events.zipWithIndex.map{case(evt,index) => {
      encodeEvent(evt, Set[Int](index), count).columnNumber
    }}
  }


}