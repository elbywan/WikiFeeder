package parsing

case class PageNode(title : String, namespace : Int)

case class SiteInfo(pageCase: String, namespaces: Map[String, Int])
