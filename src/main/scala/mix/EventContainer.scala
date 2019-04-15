package main.scala.mix

import main.scala.stats.Barcode

import scala.collection.mutable.HashMap

import scala.collection.immutable.Set

/**
  * the basic event container trait. everything we need for creating a tree from events
  */
trait EventContainer {
  def sample: String

  def events: Array[Barcode]

  def eventToCount(event: String): Int

  def eventToSites(event: String): Set[Int]

  def columnToEvent(col: Int): EventInformation

  def eventToColumn(event: String): Int

  def cellToAnnotations(event: String): HashMap[String, String]

  def rawAnnotations: HashMap[String, HashMap[String, String]]

  def allEvents: List[String]

  def numberOfTargets: Int

  def prettyPrint
}

/**
  * store all the relevant information about the editing outcomes for MIX
  *
  * @param mevents        the events as an array
  * @param meventToCount  the unique individual site events to their counts
  * @param mnumberToEvent the index of a single event to it's string representation
  * @param meventToNumber convert an event to it's index
  */
class EventContainerImpl(msample: String,
                         mevents: Array[Barcode],
                         meventToAnnotations: HashMap[String, HashMap[String, String]],
                         mnumberOfTargets: Int) extends EventContainer {

  var mTotalEvents = mevents

  def sample: String = msample

  def events: Array[Barcode] = mTotalEvents

  def eventToCount(event: String): Int = EventInformation.event(event).count

  def eventToSites(event: String): Set[Int] = EventInformation.event(event).positions

  def columnToEvent(col: Int): EventInformation = EventInformation.column(col)

  def eventToColumn(event: String): Int = EventInformation.event(event).columnNumber

  def cellToAnnotations(event: String): HashMap[String, String] = meventToAnnotations(event)

  def rawAnnotations: HashMap[String, HashMap[String, String]] = meventToAnnotations

  def allEvents: List[String] = EventInformation.orderedEvents.map{t => t.eventString}.toList

  def numberOfTargets: Int = mnumberOfTargets

  def prettyPrint() {
    println("Events")
    mTotalEvents.foreach{event => {
      println(event.prettyString())
    }}
  }

}

/**
  * storage for a subset of events over specific targets
  *
  * @param mevents          the subsetted events
  * @param eventContainer   the original events container
  * @param eventsToChildren the mapping of new ID to old ID
  */
case class SubsettedEventContainer(mevents: Array[Barcode],
                                   eventContainer: EventContainer,
                                   eventsToChildren: HashMap[String, Array[String]]) extends EventContainer {

  var mTotalEvents = mevents

  def sample: String = eventContainer.sample

  def events: Array[Barcode] = mTotalEvents

  def eventToCount(event: String): Int = eventContainer.eventToCount(event)

  def eventToSites(event: String): Set[Int] = eventContainer.eventToSites(event)

  def columnToEvent(col: Int): EventInformation = eventContainer.columnToEvent(col)

  def eventToColumn(event: String): Int = eventContainer.eventToColumn(event)

  def cellToAnnotations(event: String): HashMap[String, String] = eventContainer.cellToAnnotations(event)

  def rawAnnotations: HashMap[String, HashMap[String, String]] = eventContainer.rawAnnotations

  def numberOfTargets: Int = eventContainer.numberOfTargets

  def prettyPrint() {
    println("Events")
    mTotalEvents.foreach{event => {
      println(event.prettyString())
    }}
  }

  override def allEvents: List[String] = mevents.flatMap{e => e.events}.toList
}

object EventContainer {
  /**
    * this function takes an event container and subsets it based on specified events over specified sites. It only
    * looks at / outputs specified sites, so if you want a site included regardless of content add it as a wildcard
    *
    * @param container      the event container to generate a subset for
    * @param sitesToCapture the sites that we look at and the specified genotype (wildcard for anything)
    * @return an event container with the reduced root tree, and mapping of events in the root tree to those in each subtree
    */
  def subset(container: EventContainer,
             sitesToCapture: Array[Tuple2[Int, String]],
             sample: String): Tuple2[EventContainer, HashMap[String, Array[String]]] = {

    container.events.foreach{event => println("INPUT NODE " + event.prettyString())}

    val sitePositionLookup: Array[Int] = sitesToCapture.map { case (st, str) => st }.toArray

    val strToken = "_"
    // our new subset of events for the root partial tree
    val namesToEvents = new HashMap[String, String]()
    val nameToChildren = new HashMap[String, Array[String]]()
    val eventsToCounts = new HashMap[String, Int]()
    val eventsToProps = new HashMap[String, Double]()

    val eventsToNames = new HashMap[String, String]()

    var partialID = 0
    container.events.foreach { event => {

      // fill in a new event set for the subset event
      var partialEventArray = Array.fill[String](event.events.size)("NONE")

      // for each site position we want to include, does the current event's edit at that site get included?
      sitesToCapture.foreach { case (site, str) => {

        // get the positions this event covers
        val siteList = if (str == wildcard) {
          collection.immutable.Set[Int](site)
        } else {
          throw new IllegalStateException("We're not currently handling non ") // container.eventToSites(str)
        }

        // for now just make sure we're dealing with wildcards
        assert(str == wildcard)

        // get the positions spanned by this site
        val coveredSites = container.eventToSites(event.events(site))

        // if the event spans sites outside of this target, we don't include it
        val siteOutsiteRange = coveredSites.foldLeft[Boolean](false)((a, b) => a | !(sitePositionLookup contains b))

        // if our event doesn't extend outside, include it in our subtree
        if (!siteOutsiteRange) {
          siteList.foreach { coveredSite => partialEventArray(coveredSite) = event.events(coveredSite) }
        }

        println(partialEventArray.mkString("-") + " from " + event.events.mkString("-"))
      }
      }

      val parentEvents = partialEventArray.mkString(strToken)

      // ---------------------------------------------------------------------------------
      // do we have a parent with this signature already? if so assign it to that parent
      // ---------------------------------------------------------------------------------
      if (eventsToNames contains parentEvents) {
        val parent = eventsToNames(parentEvents)
        nameToChildren(parent) = nameToChildren(parent) :+ event.name
        println("EXISTING PARENT " + parent + " parent event " + parentEvents + " to " + nameToChildren(parent).mkString(",") + " old evt " + event.events.mkString("_"))

        eventsToCounts(parent) = eventsToCounts.getOrElse(parent, 0) + event.count
        eventsToProps(parent) = eventsToProps.getOrElse(parent, 0.0) + event.proportion

      }
      // ---------------------------------------------------------------------------------
      // else we are making a new node
      // ---------------------------------------------------------------------------------
      else {
        partialID += 1
        val parentName = "P" + partialID
        namesToEvents(parentName)   = parentEvents
        eventsToNames(parentEvents) = parentName

        // handle an edge case here -- if the parent doesn't exist
        var eventNames = nameToChildren.getOrElse(parentEvents, Array[String]())
        nameToChildren(parentName) = eventNames :+ event.name
        println("NEW PARENT " + parentName + " new parent event " + parentEvents + " to " + nameToChildren(parentName).mkString(",") + " old evt " + event.events.mkString("_"))

        eventsToCounts(parentEvents) = eventsToCounts.getOrElse(parentEvents, 0) + event.count
        eventsToProps(parentEvents) = eventsToProps.getOrElse(parentEvents, 0.0) + event.proportion

        /*container.dangerAddEvent(Event(partialEventArray,
          partialEventArray.map { st => eventsToIDs(st) },
          eventsToCounts(parentEvents),
          eventsToProps(parentEvents),
          sample,
          parentName))*/
      }
    }
    }


    (SubsettedEventContainer(nameToChildren.map { case (name, ids) => {
      println("Event in subset: " + name)
      val evt = namesToEvents(name)
      Barcode(evt.split(strToken),
        eventsToCounts(evt),
        eventsToProps(evt),
        sample,
        name)
    }
    }.toArray, container, nameToChildren), nameToChildren)
  }

  def subsetByChildren(container: EventContainer, children: Array[String], rootID: String): EventContainer = {

    val newEvents = container.events.filter {
      event => {
        println(event.name + " -- root --> " + rootID)
        (children contains event.name) || (event.name == rootID)
      }
    }

    new EventContainerImpl(container.sample,
      newEvents,
      container.rawAnnotations,
      container.numberOfTargets)


  }

  val wildcard = "*"
}
