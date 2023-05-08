package main.scala.cells

import java.io._


import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.Source

class CellAnnotations(cellFile: File) {

  // load up the annotation file
  val cells = Source.fromFile(cellFile).getLines
  val cellBuffer = new ArrayBuffer[CellAnnotation]()
  println("loading annotations from " + cellFile.getAbsolutePath)

  // we assume column one is the cell ID, two is the event string, and the rest are free-form annotations
  val headerString = cells.next().split("\t")

  val cellID = headerString(0)
  val hmid = headerString(1)
  assert(cellID == "cellID" || cellID == "index" || cellID == "cell_id","First column should be cellID or index, we saw " + headerString(0))
  assert(hmid   == "hmid","Second column should be hmid, we saw " + headerString(1))

  cells.foreach { cl => {
    val sp = cl.split("\t")
    try {
      val newCell = CellAnnotation(sp(0), sp(1))

      headerString.slice(2,headerString.size).zipWithIndex.foreach{case(headerToken,index) =>
        newCell.additionalAnnotations(headerToken) = sp(index + 2)
      }

      cellBuffer += newCell
    } catch {
      case e: Exception => {
        println("Failed on line " + sp.mkString("*")); throw e
      }
    }

  }
  }

  var allCells = cellBuffer.toArray

  /**
    * find cells that match the cell ID
    *
    * @param eventString the event string to look up
    */
  def findMatchingCells(eventString: String): Array[CellAnnotation] = {
    allCells.filter { cell => {
      if (cell.eventString == eventString) {
        if (cell.isMatchedToTerminalNode)
          println("Cell " + cell.name + " is already matched!!")
        cell.isMatchedToTerminalNode = true
        true
      } else false
    }
    }
  }

  /**
    * print any cells we haven't matched yet
    */
  def printUnmatchedCells(): Unit = {
    allCells.foreach { cell =>
      if (!cell.isMatchedToTerminalNode)
        println("Cell " + cell.name + " with event string " + cell.eventString + " IS UNMATCHED!")
    }
  }

}

case class CellAnnotation(name: String, eventString: String) {
  var isMatchedToTerminalNode = false

  val additionalAnnotations = new mutable.HashMap[String,String]()
}