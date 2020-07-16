package main.scala.mix

import java.io.{File, PrintWriter}

import main.scala.stats.Barcode

import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, HashMap}
import scala.io.Source

/**
  * Serve as an intermediate between GESTALT pipeline and MIX tool
  */
object EventIO {

  /**
    * convert a 'allEvents' file into an array of Events
    * @param allEventsFile a file with the event string first, followed by the count and proportions
    * @param sample the sample name to use
    * @return an array of events
    */
  def readEventsObject(allEventsFile: File, sample: String): EventContainer = {

    val cellAnnotations = new mutable.HashMap[String,HashMap[String,String]]()

    // keep track of the number of lines and the next available allele (event) index
    var linesProcessed = 0
    var nextIndex = 1

    val builder = ArrayBuffer[Barcode]()

    val inputFile = Source.fromFile(allEventsFile).getLines()
    val header = inputFile.next().split("\t")


    // process the input file
    inputFile.zipWithIndex.foreach { case (line, index) => {
      val lineTks = line.split("\t")

      // assign columns to the events
      EventInformation.addEvents(lineTks(0).split("_"), lineTks(2).toInt)

      // handle any annotations specified in the file
      val annotations = new mutable.HashMap[String,String]()
      header.slice(4,header.size).zipWithIndex.foreach{case(hdTk,index) => annotations(hdTk) = lineTks(index+4)}
      cellAnnotations("N" + linesProcessed) = annotations

      val evt = Barcode(lineTks(0).split("_"), lineTks(2).toInt, lineTks(3).toDouble, sample, "N" + linesProcessed)
      linesProcessed += 1
      builder += evt
    }
    }

    val evtArray = builder.toArray
    new EventContainerImpl(sample,evtArray,cellAnnotations,evtArray(0).events.size)
  }

  /**
    * convert text input into nodes
    */
  def readCellObject(allCellsFile: File, sample: String): EventContainer = {

    val cellAnnotations = new mutable.HashMap[String,HashMap[String,String]]()

    // keep track of the number of lines and the next available allele (event) index
    var linesProcessed = 0
    var nextIndex = 1

    val builder = ArrayBuffer[Barcode]()

    val inputFile = Source.fromFile(allCellsFile).getLines().toArray
    val header = inputFile(0).split("\t")


    // process the input file
    inputFile.slice(1,inputFile.size).zipWithIndex.foreach { case (line, index) => {
      val lineTks = line.split("\t")

      val cellID = "N" + linesProcessed
      val hmid = lineTks(1)
      val cells = lineTks(2)

      // assign columns to the events
      EventInformation.addEvents(hmid.replace("\"","").split("-"), 1)

      // handle any annotations specified in the file
      val annotations = new mutable.HashMap[String,String]()
      annotations("cellIDs") = cells.replace("\"","")
      cellAnnotations(cellID) = annotations

      val evt = Barcode(hmid.replace("\"","").split("-"), 1, 1.0 / (inputFile.size - 1), sample, cellID)
      linesProcessed += 1
      builder += evt
    }
    }

    val evtArray = builder.toArray
    new EventContainerImpl(sample,evtArray,cellAnnotations,evtArray(0).events.size)
  }


  /**
    * rescale occurrence values for MIX to the range of characters it accepts
    *
    * @param value the value to rescale
    * @param min   the min value observed in the data
    * @param max   the max value in the data
    * @return the scaled value as a MIX recognized character
    */
  def scaleValues(value: Int, min: Int, max: Int): Char = {
    val maxLog = math.log(max)
    val valueLog = math.log(value)
    val ret = scala.math.round(((valueLog.toDouble - min.toDouble) / maxLog.toDouble) * (EventIO.characterArray.length.toDouble - 1.0)).toInt
    EventIO.characterArray(ret)
  }


  /**
    * write the files required by the PHYLIP mix tool
    *
    * @param mixPackage the description of the files to write
    * @return an array of events that match the events that went into the tree
    */
  def writeMixPackage(mixPackage: MixFilePackage, eventsContainer: EventContainer) = {
    val weightFile = new PrintWriter(mixPackage.weightsFile)
    val mixInputFile = new PrintWriter(mixPackage.mixInputAlleles)
    println("writing " + mixPackage.weightsFile + " and " + mixPackage.mixInputAlleles)

    // normalize the weights to the range of values we have
    val maxCount = eventsContainer.allEvents.map{mp => eventsContainer.eventToCount(mp)}.max

    // -----------------------------------------------------------------------------------
    // map the each of the events to associated weights in PHYLIP space and write to disk
    // -----------------------------------------------------------------------------------
    println(eventsContainer.allEvents.mkString("."))
    val weights = eventsContainer.allEvents.map {
      case (event) => {
        scaleValues(eventsContainer.eventToCount(event), 0, maxCount)
      }
    }.toList

    println(weights.mkString(","))
    weightFile.write(weights.mkString("") + "\n")
    weightFile.close()

    var outputBuffer = Array[String]()

    var written_a_wild_type_allele = false
    var eventStringSize = -1
    eventsContainer.events.foreach { evt => {
      val outputStr = evt.toMixString()
      if (eventStringSize > 0)
        assert(eventStringSize == outputStr._3)
      else
        eventStringSize = outputStr._3

      if (!outputStr._2) {
        outputBuffer :+= outputStr._1
      } else if (!written_a_wild_type_allele && outputStr._2) {
        written_a_wild_type_allele = true
        println("WARNING: FIRST wildtype " + outputStr._1)
        outputBuffer :+= outputStr._1
      } else {
        println("WARNING: not writing allele " + outputStr._1)
      }
    }
    }

    mixInputFile.write((outputBuffer.size) + "\t" + (eventStringSize) + "\n")
    outputBuffer.foreach { case (str) => {
      mixInputFile.write(str + "\n")
    }
    }

    mixInputFile.close()
  }

  // scale the values from counts to characters (0-9, A-Z)
  val characterArray = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
}




