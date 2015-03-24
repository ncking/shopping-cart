package net.badprogrammer.shoppingcart.api

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import net.badprogrammer.platform.shoppingcart.domain.{Article, Money, Quote}
import net.badprogrammer.platform.shoppingcart.service.{AllShoppingCarts, DefaultShoppingCartIdFactory}

import scala.concurrent._

trait ActorSystemProvider {

  def system: ActorSystem

}

trait ShoppingCartSystem {

  def reference: ActorRef

  def send(message: Any)(implicit timeout: Timeout): Future[Any] = reference ? message

  def terminate: Unit

}

trait ShoppingCartSystemFactory {

  def apply(config: Config): ShoppingCartSystem

}


object LocalShoppingCartSystemFactory extends ShoppingCartSystemFactory {

  object FakeArticleRepository {

    val Flight = Article("flight")
    val HotDog = Article("hot-dog")
    val Cocaine = Article("cocaine")

    val pricing = {
      val builder = Map.newBuilder[Article, Money]
      builder += (Flight -> Money("50.50"))
      builder += (HotDog -> Money("10.10"))
      builder.result()
    }

    class ArticleHander extends Actor {
      def receive: Receive = {
        case q@Quote(_, article, _) if article == Cocaine => sender ! Quote.Unsuccessful(q, "We cannot sell drugs")
        case q@Quote(_, article, _) if article != Cocaine => sender ! Quote.Successful(q, pricing(article))
        case x => println(s"WTF is $x")
      }
    }

    def apply(implicit system: ActorSystem) = system.actorOf(Props[ArticleHander])
  }

  override def apply(config: Config): ShoppingCartSystem = new ShoppingCartSystem {

    private val system = ActorSystem("shopping-cart-system", config)

    val reference: ActorRef = system.actorOf(AllShoppingCarts.props(FakeArticleRepository(system)))

    override def terminate: Unit = system.shutdown()
  }
}