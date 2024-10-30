import cats.effect.* 
import cats.syntax.all.*
import com.comcast.ip4s.*
import org.http4s.*
import org.http4s.ember.server.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.implicits.*
import org.http4s.server.Router
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder, org.http4s.circe.CirceSensitiveDataEntityDecoder.circeEntityDecoder, org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import io.circe.*, io.circe.generic.auto.*, io.circe.parser.*, io.circe.syntax.*
import org.typelevel.log4cats.Logger, org.typelevel.log4cats.slf4j.Slf4jLogger, org.typelevel.log4cats.slf4j.loggerFactoryforSync
import fs2.kafka.*


object Server extends IOApp {

    case class GameState(
        PlayerID: String
        , BetAmount: Int
        , PlayerHand: List[String]
        , PlayerAction:String
        , DealerDownCard: String
        , DealerUpCard: String
        , HandID: String
        , Deck: List[String]
        , DealtCards: List[String]
        , PlayerStack: Int
        , GameStateID: String
    )

    implicit val decoder: EntityDecoder[IO, GameState]  = jsonOf[IO, GameState]

    val suits: List[String] = List("Clubs","Diamonds","Hearts","Spades")
    val names: List[String]= List("A","2","3","4","5","6","7","8","9","T","J","Q","K")

    val deck: List[String] = for {
        x <- names
        y <- suits
    } yield x

    val shoe: List[String] = (deck ++ deck ++ deck ++ deck ++ deck ++ deck)

    def card_values(card: String): List[Int] = 
        if (card == "A" ) List(1,11)
        else if (card == "T"  || card == "J"  || card == "Q"  || card == "K" ) List(10)
        else List(card.toInt)

    def possible_card_totals(cards: List[String]): List[Int] = 
        cards.map(x => card_values(x)).sequence.map(x => x.sum).distinct
    
    def ZeroIfBust(cards: List[String]): Int = {
        val ViableTotals = possible_card_totals(cards).filter(x => x < 22)
        if(ViableTotals.length == 0) then 0 else ViableTotals.max
    }

    def uuid = java.util.UUID.randomUUID.toString

    def DealerResponse(game: GameState): GameState =  game.PlayerAction match {
        case "S" => {
            val playertotal: Int = ZeroIfBust(game.PlayerHand)
            var new_deck: List[String] = game.Deck 
            var new_dealer_hand: List[String] = List[String](game.DealerUpCard,game.DealerDownCard)
            var new_dealt_cards: List[String] = game.DealtCards

            while (possible_card_totals(new_dealer_hand).min < 17) {
                new_dealer_hand = new_dealer_hand ++ List(new_deck.head)
                new_dealt_cards = new_dealt_cards ++ List(new_deck.head)
                new_deck = new_deck.tail
            }

            val dealertotal:Int = ZeroIfBust(new_dealer_hand)

            if (game.Deck.length < 31) {
                val response = GameState(game.PlayerID, 0 , List.empty, "B", "", "", uuid , scala.util.Random.shuffle(game.Deck ++ game.DealtCards), List.empty, if ( playertotal > dealertotal) game.PlayerStack + game.BetAmount else if (playertotal == dealertotal) game.PlayerStack  else game.PlayerStack - game.BetAmount, uuid)
                response
            }

            else {
                val response = GameState(game.PlayerID, 0, List.empty, "B", "", "", uuid , new_deck, new_dealt_cards, if ( playertotal > dealertotal) game.PlayerStack + game.BetAmount else if (playertotal == dealertotal) game.PlayerStack  else game.PlayerStack - game.BetAmount, game.GameStateID)
                response
            }
        }

        case "H" => {
            val new_player_hand: List[String] = game.PlayerHand ++ List(game.Deck(0))
            val new_dealt_cards: List[String] = game.DealtCards ++ List(game.Deck(0))
            val new_deck: List[String] = game.Deck.drop(1)
            val playertotal: Int = ZeroIfBust(new_player_hand)

            if (playertotal == 0 && new_deck.length > 31) {
                val response = GameState(game.PlayerID, 0 , List.empty, "B", "", "", uuid , new_deck, new_dealt_cards , game.PlayerStack - game.BetAmount, game.GameStateID)
                response
            }

            else if (playertotal == 0 && new_deck.length <= 31) {
                val response = GameState(game.PlayerID, 0 , List.empty, "B", "", "", uuid , scala.util.Random.shuffle(game.Deck ++ game.DealtCards), List.empty, game.PlayerStack - game.BetAmount, game.GameStateID)
                response
            }

            else {
                val response = GameState(game.PlayerID, game.BetAmount, new_player_hand, "?", game.DealerDownCard, game.DealerUpCard, game.HandID, new_deck, new_dealt_cards, game.PlayerStack, game.GameStateID)
                response
            }
        }

        case "B" => {
            val game_deck: List[String] = game.Deck
            val player_cards: List[String] = List(game_deck(0), game_deck(2))
            val dealer_down_card: String = game_deck(1)
            val dealer_up_card: String = game_deck(3)
            val response = GameState(game.PlayerID , game.BetAmount , player_cards, "?", dealer_down_card, dealer_up_card, game.HandID, game_deck.drop(4), game.DealtCards ++ game_deck.take(4), game.PlayerStack, game.GameStateID)
            response
        }

        case _ => game 
    }

    val producerSettings: ProducerSettings[IO, String, String] = ProducerSettings[IO, String, String].withBootstrapServers("localhost:9092")
    val MainKafkaProducer: Resource[IO, KafkaProducer[IO, String, String]] = KafkaProducer[IO].resource(producerSettings)
    def kafkaRecordFromGameState(playerSentGameState: GameState): ProducerRecord[String, String] = ProducerRecord("player_actions", playerSentGameState.GameStateID, playerSentGameState.asJson.noSpaces)

    def HttpApp(kafkaProducer: KafkaProducer[IO, String, String]): HttpApp[IO] = HttpRoutes.of[IO] {
        case GET -> Root / "Dealer" / playerid / playerstack => {
            val game_deck: List[String] = scala.util.Random.shuffle(shoe) 
            val GameStateResponse: GameState = GameState(playerid , 0 , List.empty, "B", "", "" , uuid , game_deck , List.empty , playerstack.toInt, uuid)
            val GameStateJSON: String = GameStateResponse.asJson.noSpaces
            Ok(GameStateJSON)
        }
        case req @ POST -> Root / "Dealer" => {
            req.as[GameState].flatMap(game => kafkaProducer.produceOne(kafkaRecordFromGameState(game))).flatten 
                >> Ok(for {game <- req.as[GameState]} yield(DealerResponse(game).asJson.noSpaces))
        }
    }.orNotFound

    def httpServer(httpApp: HttpApp[IO]) = EmberServerBuilder.default[IO].withHost(ipv4"0.0.0.0").withPort(port"8081").withHttpApp(httpApp).build
    def run(args: List[String]): cats.effect.IO[cats.effect.ExitCode] = 
        MainKafkaProducer.flatMap(kafkaProducer => httpServer(HttpApp(kafkaProducer))).use(_ => IO.never).as(ExitCode.Success)

}
