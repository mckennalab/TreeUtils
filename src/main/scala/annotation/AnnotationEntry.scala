package main.scala.annotation

import scala.collection.mutable

/**
  * annotation information for a cell
  */
// some containers
case class AnnotationEntry(taxa: String,
                           sample: String,
                           count: Int,
                           proportion: Double,
                           event:Array[String],
                           additionalEntries: mutable.HashMap[String,String])
