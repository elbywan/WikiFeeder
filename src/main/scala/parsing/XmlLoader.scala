package parsing

import org.xml.xtended.core.XtendedXmlParser
import org.xml.xtended.core.XtendedXmlParser.TagOpened

import scala.annotation.tailrec
import scala.io.Source
import scala.xml.pull.{EvElemEnd, EvText, EvElemStart}

object XmlLoader {

    def loadFromXML(path: String, worker: Worker) {
        val source = Source.fromFile(path)
        val parser = new XtendedXmlParser(source)

        // Populating site info
        parser.goToByName[EvElemStart](TagOpened("siteinfo"))
        parser.goToByName[EvElemStart](TagOpened("case"))
        val pageCase = parser.getTagContents().get
        parser.goToByName[EvElemStart](TagOpened("namespaces"))
        def getNamespaces(acc : Map[String, Int] = Map()) : Map[String, Int] = {
            parser.next() match {
                case EvElemStart(_, "namespace", attributes, _) =>
                    val key = attributes.asAttrMap.getOrElse("key", "0").toInt
                    if(key != 0)
                        getNamespaces(acc + (parser.next().asInstanceOf[EvText].text -> key))
                    else
                        getNamespaces(acc)
                case EvElemEnd(_, "namespaces") =>
                    acc
                case _ =>
                    getNamespaces(acc)
            }
        }
        val namespaces = getNamespaces()

        val siteInfo = SiteInfo(pageCase, namespaces)
        worker.writeSiteInfo(siteInfo)
        ////

        val pageTag_opened = TagOpened("page")

        val findTag = parser.goToByName[EvElemStart](pageTag_opened)

        @tailrec
        def loop(findTag: EvElemStart): Unit = {
            if (findTag == null) {
                ()
            } else {
                val page_raw = parser.getTagContents.get
                worker.feed(page_raw, siteInfo)
                loop(parser.goToByName[EvElemStart](pageTag_opened).get)
            }
        }

        try {
            loop(findTag.get)
        } finally {
            worker.shutdown()
            parser stop()
        }

    }
}
