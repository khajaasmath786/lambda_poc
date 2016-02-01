package model

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.json4s._
import org.json4s.jackson.JsonMethods._

object Event {
  private val format = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

  val EVENT_TYPES = Seq(
    "LOGIN_WEB", "LOGIN_MOBILE", "BANK_TRANSFER",
    "PAY_BILL", "REQUEST_CREDIT_CARD", "VIEW_STATEMENT",
    "REQUEST_MORTGAGE", "PAY_CREDIT_CARD", "CREATE_ACCOUNT",
    "ENABLE_APPLE_PAY", "REQUEST_SMS", "REQUEST_PAPER_STATEMENT"
  )

  def convertStringToDate(dateString: String): DateTime = format.parseDateTime(dateString)

  def fromJson(jsonEvent: String): Event = {
    implicit val formats = DefaultFormats
    val parsed = parse(jsonEvent)
    parsed.extract[Event]
  }

}

case class Event(timestamp: String, `type`: String) {
  val date = Event.convertStringToDate(timestamp)
  val buckets = BucketList(date)

  val year = date.getYear
  val month = date.monthOfYear().get()
  val day = date.dayOfMonth.get()
  val hour = date.hourOfDay.get()
  val minute = date.minuteOfHour.get()
}