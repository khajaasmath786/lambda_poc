package batch_layer

import utils.Environment
import utils.Utils.measureTime

object BatchPipeline {

  def main(args: Array[String]) {
    
    //Getting SQL context from Environment.Scala class . It has other contexts too when needed.
    val sqlContext = Environment.SPARK.newSqlContext("BatchPipeline")

    
    // This method inside the same class will call cassandra and create keyspaces if not present. Making cassandra ready to store the data.
    // This method also deletes data from hdfs://localhost:9000/events which is acting as output directory. This output directory is created when processing the JSON files and saving it as Dataframe in the path.
    prepareEnvForTest(Environment.CASSANDRA.HOST, Environment.HDFS.HOST, Environment.HDFS.EVENTS_MASTERDATASET_DIR)

    
    // Measure time will take block of expression as commands and gives how much time it took to complete processing.
    // PreProcessData will read the Json files from the hdfs directory /new_data/kafka/events_topic whose filename!= .tmp, and creates dataframe in output dir /events (EVENTS_MASTERDATASET_DIR)
    measureTime {
      DataPreProcessing.preProcessData(sqlContext, Environment.HDFS.HOST, Environment.HDFS.FLUME_DATA_DIR, Environment.HDFS.EVENTS_MASTERDATASET_DIR, false)
    }
    
    // Measure time will take block of expression as commands and gives how much time it took to complete processing.
    //process data will pick the files from output dir /events( EVENTS_MASTERDATASET_DIR ) and then saves the data into cassandra database.
    // This folder is deleted when processed next time as data is already saved in casssandra.
    measureTime {
      DataProcessor.processData(sqlContext, Environment.HDFS.EVENTS_MASTERDATASET_DIR)
    }

    sqlContext.sparkContext.stop()
    System.exit(0)
  }

  def prepareEnvForTest(cassandraHost: String, hdfsUrl: String, outputDir: String) {
    HdfsUtils.deleteFile(HdfsUtils.getFileSystem(hdfsUrl), outputDir, true)
    test.PrepareDatabase.prepareBatchDatabase(cassandraHost) // here test is package name. calling object with package name instead of importing that package.
  }
}
