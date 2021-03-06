package com.criteo.dev.cluster.aws

import com.criteo.dev.cluster._
import com.criteo.dev.cluster.command.{ScpAction, SshAction, SshMultiAction}
import com.criteo.dev.cluster.config.TargetConfig
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

/**
  * Copy configuration over to a node.
  */
object CopyConfAction {

  private val logger = LoggerFactory.getLogger(CopyConfAction.getClass)

  /**
    *
    * @param targetConf
    * @param cluster
    * @param mount
    * @return
    */
  def apply(targetConf: TargetConfig, cluster: JcloudCluster, mount: List[String]) = {
    logger.info(s"Copying Hadoop configuration to ${cluster.size} nodes in parallel.")

    val srcDir = {
      val confOverride = targetConf.common.hadoopConfDir
      if (!confOverride.isEmpty) {
        s"${GeneralConstants.hadoopConfSrcDir}/$confOverride"
      } else {
        s"${GeneralConstants.hadoopConfSrcDir}/${GeneralConstants.clusterConfDefault}"
      }
    }

    val masterFuture = GeneralUtilities.getFuture {
      val master = NodeFactory.getAwsNode(targetConf.aws, cluster.master)
      SshAction(master, "mkdir -p ./conf")
      ScpAction(
        sourceN=None,
        srcPath = s"$srcDir/hadoop/",
        targetN= Some(master),
        targetPath="./conf/")

      ScpAction(
        sourceN = None,
        srcPath = s"$srcDir/hive/",
        targetN = Some(master),
        targetPath = "./conf/")

      ScpAction(
        sourceN = None,
        srcPath = s"$srcDir/spark/",
        targetN = Some(master),
        targetPath = "./conf/")

      val confAction = new SshMultiAction(master)
      confAction.add("sudo cp -r ~/conf/hadoop/conf/* /etc/hadoop/conf")
      confAction.add("sudo cp -r ~/conf/hive/conf/* /etc/hive/conf")
      confAction.add("sudo cp -r ~/conf/spark/conf/* /etc/spark/conf")
      confAction.add("rm -r ./conf")

      //TODO- we could do hadoop-conf template resolution using Apache Velocity.
      confAction.add("sudo find /etc/hadoop/conf/ -name \"*.xml\" -type f -exec sed -i 's/$" +
        GeneralConstants.master + "/" + GeneralConstants.masterHostName + "/' {} +")
      confAction.add("sudo find /etc/hadoop/conf/ -name \"*.xml\" -type f -exec sed -i 's/$" +
        GeneralConstants.local + "/" + GeneralConstants.masterHostName + "/' {} +")

      val keyId = targetConf.aws.accessId
      val key = targetConf.aws.accessKey
      confAction.add("sudo find /etc/hadoop/conf/ -name \"*.xml\" -type f -exec sed -i 's/$" +
        GeneralConstants.accessKey + "/" + keyId + "/' {} +")
      confAction.add("sudo find /etc/hadoop/conf/ -name \"*.xml\" -type f -exec sed -i 's/$" +
        GeneralConstants.secretKey + "/" + key + "/' {} +")

      confAction.add("sudo find /etc/hive/conf/ -name \"*.xml\" -type f -exec sed -i 's/$" +
        GeneralConstants.master + "/" + GeneralConstants.masterHostName + "/' {} +")
      confAction.add("sudo find /etc/hive/conf/ -name \"*.xml\" -type f -exec sed -i 's/$" +
        GeneralConstants.local + "/" + GeneralConstants.masterHostName + "/' {} +")

      confAction.add("sudo find /etc/spark/conf/ -name \"*.xml\" -type f -exec sed -i 's/$" +
        GeneralConstants.master + "/" + GeneralConstants.masterHostName + "/' {} +")
      confAction.add("sudo find /etc/spark/conf/ -name \"*.xml\" -type f -exec sed -i 's/$" +
        GeneralConstants.local + "/" + GeneralConstants.masterHostName + "/' {} +")

      confAction.add("sudo find /etc/hadoop/conf/ -name \"*.xml\" -type f -exec sed -i 's;$" +
        GeneralConstants.dataDir + ";" + GeneralUtilities.getDataDir(mount) + ";' {} +")
      confAction.run()
      ""  //Otherwise NPE is thrown
    }

    val allFutures = Set(masterFuture) ++ cluster.slaves.map(s => GeneralUtilities.getFuture {
      val slaveNode = NodeFactory.getAwsNode(targetConf.aws, s)
      SshAction(slaveNode, "mkdir -p ./conf")
      ScpAction(
        sourceN=None,
        srcPath = s"$srcDir/hadoop",
        targetN= Some(slaveNode),
        targetPath="./conf/")

      val confAction = new SshMultiAction(slaveNode)
      confAction.add("sudo cp -r ~/conf/hadoop/conf/* /etc/hadoop/conf")
      confAction.add("rm -r ./conf")

      //TODO- we could do hadoop-conf template resolution using Apache Velocity.
      val slaveHost = s.getUserMetadata.get(AwsConstants.hostName)
      confAction.add("sudo find /etc/hadoop/conf/ -name \"*.xml\" -type f -exec sed -i 's/$" +
        GeneralConstants.master + "/" + GeneralConstants.masterHostName + "/' {} +")
      confAction.add("sudo find /etc/hadoop/conf/ -name \"*.xml\" -type f -exec sed -i 's/$" +
        GeneralConstants.local + "/" + slaveHost + "/' {} +")

      val keyId = targetConf.aws.accessId
      val key = targetConf.aws.accessKey
      confAction.add("sudo find /etc/hadoop/conf/ -name \"*.xml\" -type f -exec sed -i 's/$" +
        GeneralConstants.accessKey + "/" + keyId + "/' {} +")
      confAction.add("sudo find /etc/hadoop/conf/ -name \"*.xml\" -type f -exec sed -i 's/$" +
        GeneralConstants.secretKey + "/" + key + "/' {} +")
      confAction.add("sudo find /etc/hadoop/conf/ -name \"*.xml\" -type f -exec sed -i 's;$" +
        GeneralConstants.dataDir + ";" + GeneralUtilities.getDataDir(mount) + ";' {} +")
      confAction.run()
      ""  //Otherwise NPE is thrown
    })

    val aggFuture = Future.sequence(allFutures)
    Await.result(aggFuture, Duration.Inf)
  }
}
