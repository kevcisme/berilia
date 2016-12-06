package com.criteo.dev.cluster.aws

import com.criteo.dev.cluster._
import org.slf4j.LoggerFactory


/**
  * Create a multi-node CDH cluster.
  */
@Public object CreateAwsCliAction extends CliAction[AwsCluster] {

  private val logger = LoggerFactory.getLogger(CreateAwsCliAction.getClass)

  override def command : String = "create-aws"

  override def usageArgs = List("nodes", Option("copy.dir"))

  override def help = "Creates an AWS multi-node cluster from the base OS image provided by AWS.  Runs all the " +
    "install scripts and custom overrides."


  def applyInternal(args: List[String], conf: Map[String, String]) : AwsCluster = {
    val nodes = Integer.valueOf(args(0))

    require(nodes > 0, "Invalid number of nodes")
    val baseImage = GeneralUtilities.getConfStrict(conf, AwsConstants.baseImageId, GeneralConstants.targetAwsProps)

    //create AWS instance(s) from OS base image.
    val cluster = CreateClusterAction(conf, nodes, baseImage, baseImage)

    //configure hosts.
    ConfigureHostsAction(conf, List(cluster))

    //set up passwordless SSH from master to workers.
    // Not necessary unless we use the cluster startup scripts.
    // PasswordlessSshAction(conf, cluster)

    //Install CDH5.5 (do not start services)
    InstallHadoopAction(conf, cluster)

    //Copy over Hadoop-configurations
    CopyConfAction(conf, cluster)

    //Copy over user-data (similar to mount in local cluster mode)
    if (args.length == 2) {
      val toCopy = args(1)
      CopyDirCliAction.copyDir(NodeFactory.getAwsNode(conf, cluster.master), toCopy)

    }

    //Start services.
    StartClusterAction(conf, List(cluster))

    AwsUtilities.printClusterInfo(conf, cluster)

    AwsUtilities.getAwsCluster(conf, cluster)
  }
}