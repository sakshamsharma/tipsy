package tipsy.frontend

import tipsy.compare._
import tipsy.db.TipsyPostgresProfile.api._
import tipsy.db.schema._

import java.io.File
import java.io.PrintWriter

import scala.concurrent.{ Future, Await }
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext

trait CLIClusterHelpers extends TipsyDriver with ClusterActions {
  implicit val config: Config
  implicit val executionContext: ExecutionContext

  def cliUpdateClusters(implicit quesId: String) = {
    val action = for {
      matrix <- driver.runDB {
        distTable.filter(_.quesId === quesId).map(e => (e.id1, e.id2, e.dist)).result
      }
      _ <- doUpdateClusters(matrix, quesId)
    } yield ()
    Await.result(action, Duration.Inf)
  }

  def cliMatrixDump(implicit quesId: String) = {
    val action = for {
      matrix <- driver.runDB {
        distTable.filter(_.quesId === quesId).map(e => (e.id1, e.id2, e.dist)).result
      }
      matrixStr = Dists.getAsJson(matrix)
      writer = new PrintWriter(new File(s"matrix_${quesId}"))
      _ <- Future(writer.write(matrixStr))
      _ <- Future(writer.close())
      _ <- Future(println("Finished task"))
    } yield ()
    Await.result(action, Duration.Inf)
  }

  def cliVariance(implicit quesId: String) = {
    val action = for {
      clusters <- driver.runDB {
        clusterTable.filter(_.quesId === quesId).map(_.cluster).result
      }.map(_.headOption.getOrElse(throw new Exception(s"Cluster for ${quesId} not found in database.")))

      _ <- Future(println("SINGLETON CLUSTER COUNT: " ++ clusters.filter(_.length == 1).length.toString))
      _ <- Future(println("TOTAL NUMBER OF CLUSTERS: " ++ clusters.length.toString))

      varsAndLen <- Future.sequence(
        clusters.map { cluster =>
          if (cluster.length > 1) {
            for {
              progs <- Future.sequence(cluster.map(progId =>
                driver.runDB {
                  progTable.filter(_.id === progId).result
                }.map(_.headOption.getOrElse(throw new Exception(s"Could not find program ${progId}")))
              ))
              scores = progs.map(_.score.toDouble)
              variance = getVariance(scores)
              _ <- Future(println(s"${scores.length.toString} -> ${variance.toString}"))
            } yield (variance, scores.length, scores)
          } else Future(0.0, 0, List())
        }
      )
    } yield varsAndLen

    val varsAndLen = Await.result(action, Duration.Inf)

    println("Weighted average of non-singleton variance: " ++
      (varsAndLen.map(x => x._1 * x._2).sum / varsAndLen.map(_._2).sum).toString)
    println("Non-singleton variance: " ++
      getVariance(varsAndLen.flatMap(_._3)).toString)
    println("Non-singleton programs count: " ++
      varsAndLen.map(_._2).sum.toString)
    println("Overall variance: " ++
      Await.result(findVarianceOfQues(quesId), Duration.Inf).toString)
  }
}
