package speed_layer

import kafka.serializer.StringDecoder
import model.BucketModel.BucketTypes
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{DataFrame, SQLContext, SaveMode}
import org.apache.spark.streaming.dstream.InputDStream
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}
import utils.DataFrameUtils._
import utils.Environment
import utils.Utils.measureTime

object StreamingEventCounter {

  def main(args: Array[String]) {
    test.PrepareDatabase.prepareRealTimeDatabase(Environment.CASSANDRA.HOST)

    val ssc = Environment.SPARK.newStreamingContext("StreamingEventCounter", Seconds(1))
    val inputStream:InputDStream[(String, String)]=getStream(ssc);
    //From InputStream, each message will be an RDD so you can see foreachRDD will give an RDD from input stream
    inputStream.map { case (key, value) => value }.foreachRDD(rdd => {
      measureTime {
        if (!rdd.isEmpty()) processMessagesRdd(rdd, new SQLContext(ssc.sparkContext))
      }
    })

    ssc.start()
    ssc.awaitTermination()
  }

  def processMessagesRdd(rdd: RDD[String], sqlContext: SQLContext) = {
    
    // Processing JSON RDD and converting it to RDD
    val df = sqlContext.read.json(rdd)
    
/*  {"event":"AAA", "timestamp":"2015-06-10 12:54:43"}
    {"event":"AAA", "timestamp":"2015-06-10 12:54:43"}
    {"event":"AAA", "timestamp":"2015-06-10 14:54:43"} 
    {"event":"ZZZ", "timestamp":"2015-06-25 12:54:43"}
    {"event":"ZZZ", "timestamp":"2015-06-25 12:54:53"}*/

    println("Aggregating by minute...")
    val eventsPerMinute = df.groupBy(col("event"), bucketStartDateCol(BucketTypes.minute, "timestamp") as "bdate").count().cache()

    println(s"Saving data with bucket [m] : DF size => ${eventsPerMinute.count()}")
    saveToCassandra(eventsPerMinute.withBucketColumn(BucketTypes.minute))

    println("Aggregating by hour...")
    val eventsPerHour = eventsPerMinute.groupBy(col("event"), bucketStartDateCol(BucketTypes.hour, "bdate") as "bdate").agg(sum("count") as "count")

    println(s"Saving data with bucket [H] : DF size => ${eventsPerHour.count()}")
    saveToCassandra(eventsPerHour.withBucketColumn(BucketTypes.hour))

    eventsPerMinute.unpersist()
  }

  def getStream(ssc: StreamingContext): InputDStream[(String, String)] = {
    val kafkaParams = Map[String, String](
      "metadata.broker.list" ->  Environment.KAFKA.BROKER_LIST,
      "group.id" -> "speed_layer"
    )
    KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](ssc, kafkaParams, Set("events_topic"))
  }

  //TODO try to use the low level API for batching inserts and try session.executeAsynch
  def saveToCassandra(df: DataFrame) =
    df.
      write.mode(SaveMode.Append).format("org.apache.spark.sql.cassandra")
      .options(Map("table" -> "events", "keyspace" -> "lambda_poc"))
      .save()
}
