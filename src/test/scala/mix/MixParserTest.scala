import main.scala.mix.{EventContainer, EventInformation, MixParser}
import main.scala.stats.Barcode
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.immutable.Set
import scala.collection.mutable

class ScalaTestRunnerTests extends AnyWordSpec with Matchers {
  val fakeEventsToNumbers = new EventContainer() {
    override def sample: String = "fake"
    override def events: Array[Barcode] = Array[Barcode]()
    override def eventToCount(event: String): Int = 10
    override def eventToSites(event: String): Set[Int] = Set(0)
    override def columnToEvent(col: Int): EventInformation = new EventInformation("test", 0, Set(0))
    override def eventToColumn(event: String): Int = 0
    override def cellToAnnotations(event: String): mutable.HashMap[String, String] = new mutable.HashMap[String, String]()
    override def rawAnnotations: mutable.HashMap[String, mutable.HashMap[String, String]] = new mutable.HashMap[String, mutable.HashMap[String, String]]()
    override def allEvents: List[String] = List[String]()
    override def numberOfTargets: Int = 100
    override def prettyPrint: Unit = {}
  }

  "Scalatest runnner" should {
    "convert string true to boolean true" in {
      "true".toBoolean shouldBe true
    }
    "convert string false to boolean false" in {
      "false".toBoolean shouldBe false
    }
    "test that we find the right tokens" in {

      val parser = new MixParser("test_data/mix_outfile_test",
        1219,
        0,
        "test",
        5)

    }
/*
    "test that we find the right tokens tree 2" in {

      val parser = new MixParser("test_data/mix_outfile_test2",259, 0, "test")
      info("Testing: " + parser.splits.length)
      for (split <- parser.splits) {
        //info("split: " + split)
        for (patternMatch <- parser.extractor_regex.findAllMatchIn(split)) {
          //info(s"${patternMatch.groupCount}")
          val alleles = patternMatch.group(4).replace(" ","")
          info(s"from : ${patternMatch.group(1)} to: ${patternMatch.group(2)} mod: ${patternMatch.group(3)} alleles ${alleles}")
        }
      }
      " with spaces ".trim() shouldBe "with spaces"
    }*/
  }
}