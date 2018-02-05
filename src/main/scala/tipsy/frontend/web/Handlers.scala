package tipsy.frontend.web

import tipsy.frontend.Requests
import tipsy.compiler._
import tipsy.compare._
import tipsy.parser._
import tipsy.db._
import tipsy.db.schema._
import tipsy.db.TipsyPostgresProfile.api._
import tipsy.frontend._

import akka.http.scaladsl.model.StatusCodes._
import scala.concurrent.Future

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

trait Handlers extends JsonSupport with TableHandlers with Helpers {

  def similarFromDB(id: Int): HandleResp = {
    val r = getFromDB(id)
    getFromDB(id).flatMap(_ match {
      case None => Future((NotFound, "Program not found".asJson))
      case Some(prog) =>
        val progsFuture = SimilarProgs(prog)
        progsFuture.map { progs =>
          val res = Map("similar" -> progs.toString.asJson,
            "count" -> progs.length.asJson).asJson
            (OK, res)
        }
    })
  }

  def progFromDB(id: Int): HandleResp = {
    getFromDB(id).map(_ match {
      case None => ((NotFound, "Program not found".asJson))
      case Some(x) => (OK, x.asJson)
    })
  }

  def compiledFromDB(id: Int): HandleResp = {
    getFromDB(id).map(_ match {
      case None => ((NotFound, "Program not found".asJson))
      case Some(p: Program) =>
        Compiler(p.code) match {
          case Left(err) => (BadRequest, err.toString.asJson)
          case Right(tree: ParseTree) =>
            val res = Map("tree" -> tree.toString.asJson,
              "flow" -> tree.compress.toString.asJson).asJson
            (OK, res)
        }
    })
  }

  def correctProgramFromReq(progreq: Requests.ProgramInsertReq): HandleResp = {
    Compiler.compileWithStats(progreq) match {
      case Left(err) => Future(BadRequest, ("Compilation failed: " ++ err.toString).asJson)
      case Right(prog) => correctGivenCode(prog)
    }
  }

  def correctProgramFromDB(id: Int): HandleResp = {
    for {
      prog <- getFromDB(id)
      res <- prog match {
        case None => Future((NotFound, "Program not found".asJson))
        case Some(prog) => correctGivenCode(prog)
      }
    } yield res
  }

  private def correctGivenCode(prog: Program): HandleResp = {
    Compiler(prog.code) match {
      case Left(err) => Future((BadRequest, ("Compilation failed: " ++ err.toString).asJson))
      case Right(mainTree: ParseTree) =>
        NormalizeParseTree(mainTree) match {
          case Left(err) => Future((BadRequest, ("Normalization failed: " ++ err.toString).asJson))
          case Right(nc) => for {
            res <- Compare.suggestCorrections(nc)(prog.quesId)
          } yield (OK, res.asJson)
        }
    }
  }

  def getProgCount(): HandleResp = {
    val myQuery: Query[Rep[Int], Int, Seq] = progTable.map(_.id)
    for {
      progs <- driver.runDB(myQuery.result)
      res = Map(
        "Available programs" -> progs.asJson,
        "Count" -> progs.length.asJson
      ).asJson
    } yield (OK, res)
  }
}
