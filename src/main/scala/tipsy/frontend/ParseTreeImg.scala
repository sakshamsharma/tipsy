package tipsy.frontend

import scala.io.Source
import tipsy.compiler.WorkflowCompiler

import scala.util.{Try, Success, Failure}

import reftree.render._
import reftree.contrib._
import reftree.diagram._
import java.nio.file.Paths

/**
  * ParseTreeImg: Frontend to display the parse tree
  * using graphviz and reftree
  */
object ParseTreeImg extends Draw {
  val renderer = Renderer(
    renderingOptions = RenderingOptions(density = 75),
    directory = Paths.get(".")
  )

  def apply(args: Array[String]): Unit = {
    args.map(x => {
      println("Compiling " + x)
      val source = Try(Source.fromFile(x).mkString)

      source match {
        case Success(c) => {
          WorkflowCompiler(c) match {
            case Right(tree) => {
              println(tree)
              renderer.render("parsetree", Diagram(tree))
            }
            case Left(err) => {
              println("Error =>")
              println(err)
            }
          }
        }
        case Failure(e) => println(e)
      }
    })
  }
}