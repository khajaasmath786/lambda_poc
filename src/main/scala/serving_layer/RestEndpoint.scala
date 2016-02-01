package serving_layer

import akka.actor.ActorSystem
import com.datastax.driver.core.Cluster
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import spray.http.HttpHeaders.RawHeader
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.routing.SimpleRoutingApp

object MJsonImplicits extends DefaultJsonProtocol {
  implicit val impEvent = jsonFormat3(WEvent)

  case class WEvent(bucket: String, date: String, count: Long)

}

object RestEndpoint extends App with SimpleRoutingApp with SprayJsonSupport {
  implicit val system = ActorSystem("spray-actor")

  startServer(interface = "localhost", port = 9999) {
    import MJsonImplicits._

    respondWithHeader(RawHeader("Access-Control-Allow-Origin", "*")) {
      path("lambda" / "countbyperiod") {
        get {
          parameters('from, 'to, 'event) { (from, to, event) =>
            complete {
              val result = EventCount.countEventsByRange(event, new Range(new DateTime(from), new DateTime(to)))
              (event, from, to, result)
            }
          }
        }
      } ~
        path("lambda" / "timeseries") {
          get {
            parameters('from, 'to, 'event, 'bucket) { (from, to, event, bucket) =>
              complete {
                val results = EventCount.getEventCountByRangeAndBucket(event, new Range(new DateTime(from), new DateTime(to)), bucket)
                results.map(e => WEvent(bucket, e.bucket.date.toString("yyyy-MM-dd HH:mm"), e.count))
              }
            }
          }
        } ~
        path("lambda" / "getevents") {
          get {
            parameters('from, 'to, 'event) { (from, to, event) =>
              complete {
                var results = Seq.empty[EventRow]

                if (StringUtils.isEmpty(event)){
                  println("aaaaa")
                  results = EventCount.getTotalEventsCount(new Range(new DateTime(from), new DateTime(to)))
                }
                else {
                  println("bbbb")
                  results = EventCount.getEventsByRange(event, new Range(new DateTime(from), new DateTime(to)))
                }

                results.map(e => WEvent(e.bucket.b_type, e.bucket.date.toString("yyyy-MM-dd HH:mm"), e.count))
              }
            }
          }
        }
    }


  }
}