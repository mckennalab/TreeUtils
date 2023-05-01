package main.scala.annotation

import main.scala.mix.EventContainer

import scala.collection.mutable.HashMap

/**
  * load up all the annotation files, and make a set of mappings
  * for each node name to a set of annotations.  On request,
  * annotate each node with its appropriate info
  */
class AnnotationsManager(evtContainer: EventContainer) {
  val seperator = "\t"
  val eventSeperator = "_"

  val annotationMapping = new HashMap[String,AnnotationEntry]()
  val cladeMapping = new HashMap[String,CladeEntry]()
  val sampleTotals = new HashMap[String,Int]()

  var eventDefinitionsToAnnotations = evtContainer.rawAnnotations

  evtContainer.events.foreach{evt => {
    //println("adding event with " + evt.events.size)
    val annotations = evtContainer.rawAnnotations.getOrElse(evt.name,new HashMap[String,String]())
    if (annotations.size == 0)
      println("No annotations for cell " + evt.name)
    annotationMapping(evt.name) = AnnotationEntry("UNKNOWN",evt.name,evt.count,evt.proportion,evt.events,annotations)
    cladeMapping(evt.name) = CladeEntry(evtContainer.sample,"ALL","black")
    sampleTotals(evt.sample) = sampleTotals.getOrElse(evt.sample,0) + evt.count
  }}
}
