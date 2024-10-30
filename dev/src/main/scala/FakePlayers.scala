import cats.effect.unsafe.implicits.global
import cats.effect.* 
import cats.syntax.all.*
import org.http4s.client._
import org.http4s.Uri
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
import scala.math.pow
import com.comcast.ip4s.*


object FakePlayers extends IOApp.Simple {
  
    val httpClient = EmberClientBuilder.default[IO].build
    val DealerEndpoint = "http://localhost:8081/Dealer"

    val rand = new scala.util.Random

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

    def SendPayloadIO(payload: GameState, target: String): IO[GameState] = {
        val req = Request[IO](
                method = Method.POST, 
                uri = Uri.fromString(target).valueOr(throw _) 
                ).withEntity(payload.asJson)
        httpClient.use{client => client.expect[GameState](req) }
    }

    def HighLowCardValue(card: String): Int =  {
        if (card == "T"  || card == "J"  || card == "Q"  || card == "K" || card == "A") -1
        else if (card == "7" || card == "8" || card == "9") 0
        else 1
    }

    def CardCountTotal(dealt_cards: List[String]): Int = {
        dealt_cards.map(x => HighLowCardValue(x)).sum
    }

    def BasicStrategyShouldStand(hand: List[String], dealer_card: String): Boolean = {
        val player_value = ZeroIfBust(hand)
        val dealer_card_value = if (dealer_card == "T"  || dealer_card == "J"  || dealer_card == "Q"  || dealer_card == "K" || dealer_card == "A") 10 else dealer_card.toInt
        if (player_value == 17 ) true 
        else if (player_value >= 13 && dealer_card_value <= 6) true
        else if (player_value == 12 && List(4,5,6).contains(dealer_card_value)) true 
        else false
    }


    def StandOn17Strategy(game: GameState): IO[GameState] = 
        if (game.PlayerAction == "B") {
            val payload = GameState(game.PlayerID,rand.between(1,1000),game.PlayerHand, game.PlayerAction, game.DealerDownCard, game.DealerUpCard, game.HandID, game.Deck,game.DealtCards, game.PlayerStack,game.GameStateID)
            SendPayloadIO(payload, DealerEndpoint)
        }
        else if (game.PlayerAction == "?") {
            if(ZeroIfBust(game.PlayerHand) >= 17) {
                val payload = GameState(game.PlayerID,game.BetAmount,game.PlayerHand, "S", game.DealerDownCard, game.DealerUpCard, game.HandID, game.Deck,game.DealtCards, game.PlayerStack,game.GameStateID)
                SendPayloadIO(payload, DealerEndpoint)
            }
            else {
                val payload = GameState(game.PlayerID,game.BetAmount,game.PlayerHand, "H", game.DealerDownCard, game.DealerUpCard, game.HandID, game.Deck,game.DealtCards, game.PlayerStack,game.GameStateID)
                SendPayloadIO(payload, DealerEndpoint)
            }
        }
        else IO(game)


    def RandomStrategy(game: GameState): IO[GameState] = 
        if (game.PlayerAction == "B") {
            val payload = GameState(game.PlayerID,rand.between(1,1000),game.PlayerHand, game.PlayerAction, game.DealerDownCard, game.DealerUpCard, game.HandID, game.Deck,game.DealtCards, game.PlayerStack,game.GameStateID)
            SendPayloadIO(payload, DealerEndpoint)
        }
        else if (game.PlayerAction == "?") {
            if(rand.between(1,1000) <= 500 ) {
                val payload = GameState(game.PlayerID,game.BetAmount,game.PlayerHand, "S", game.DealerDownCard, game.DealerUpCard, game.HandID, game.Deck,game.DealtCards, game.PlayerStack,game.GameStateID)
                SendPayloadIO(payload, DealerEndpoint)
            }
            else {
                val payload = GameState(game.PlayerID,game.BetAmount,game.PlayerHand, "H", game.DealerDownCard, game.DealerUpCard, game.HandID, game.Deck,game.DealtCards, game.PlayerStack,game.GameStateID)
                SendPayloadIO(payload, DealerEndpoint)
            }
        }
        else IO(game)

    def BasicStrategy(game: GameState): IO[GameState] = 
        if (game.PlayerAction == "B") {
            val payload = GameState(game.PlayerID,100,game.PlayerHand, game.PlayerAction, game.DealerDownCard, game.DealerUpCard, game.HandID, game.Deck,game.DealtCards, game.PlayerStack,game.GameStateID)
            SendPayloadIO(payload, DealerEndpoint)
        }
        else if (game.PlayerAction == "?") {
            if( BasicStrategyShouldStand(game.PlayerHand, game.DealerUpCard) ) {
                val payload = GameState(game.PlayerID,game.BetAmount,game.PlayerHand, "S", game.DealerDownCard, game.DealerUpCard, game.HandID, game.Deck,game.DealtCards, game.PlayerStack,game.GameStateID)
                SendPayloadIO(payload, DealerEndpoint)
            }
            else {
                val payload = GameState(game.PlayerID,game.BetAmount,game.PlayerHand, "H", game.DealerDownCard, game.DealerUpCard, game.HandID, game.Deck,game.DealtCards, game.PlayerStack,game.GameStateID)
                SendPayloadIO(payload, DealerEndpoint)
            }
        }
        else IO(game)


    def CardCountingWithBasicStrategy(game: GameState): IO[GameState] = 
        if (game.PlayerAction == "B") {
            val TheCount = CardCountTotal(game.DealtCards)
            val StrategicBetAmount: Int = if (TheCount < 0) pow(100,TheCount).toInt else if (TheCount == 0) 100 else TheCount*100
            val payload = GameState(game.PlayerID,StrategicBetAmount,game.PlayerHand, game.PlayerAction, game.DealerDownCard, game.DealerUpCard, game.HandID, game.Deck,game.DealtCards, game.PlayerStack,game.GameStateID)
            SendPayloadIO(payload, DealerEndpoint)
        }

        else if (game.PlayerAction == "?") {
            if( BasicStrategyShouldStand(game.PlayerHand, game.DealerUpCard) ) {
                val payload = GameState(game.PlayerID,game.BetAmount,game.PlayerHand, "S", game.DealerDownCard, game.DealerUpCard, game.HandID, game.Deck,game.DealtCards, game.PlayerStack,game.GameStateID)
                SendPayloadIO(payload, DealerEndpoint)
            }
            else {
                val payload = GameState(game.PlayerID,game.BetAmount,game.PlayerHand, "H", game.DealerDownCard, game.DealerUpCard, game.HandID, game.Deck,game.DealtCards, game.PlayerStack,game.GameStateID)
                SendPayloadIO(payload, DealerEndpoint)
            }
        }

        else IO(game)

    val PlayersIds = (1 to 100).map(x => s"StandOn17$x") ++ (1 to 100).map(x => s"Random$x") ++ (1 to 100).map(x => s"Basic$x") ++ (1 to 5).map(x => s"Counter$x")

    def StartGameState(playerid: String, playerstacksize: Int): IO[GameState] = {
            val target = s"http://localhost:8081/Dealer/$playerid/$playerstacksize" 
            httpClient.use{client => client.expect[GameState](target)} 
    }

    val InitializeGameState: IndexedSeq[IO[GameState]] = PlayersIds.map(x => StartGameState(x, 5000))

    def PlayGameState(states: IndexedSeq[IO[GameState]]): IndexedSeq[IO[GameState]] = {
        val new_states = states.map(x => x.unsafeRunSync())
        println(new_states)
        if (new_states.exists(x => x.PlayerStack > 0 )) {
            val next_states = new_states.map(x =>  {
                    if (x.PlayerID(0).toString == "S" && x.PlayerStack > 0) StandOn17Strategy(x)
                    else if (x.PlayerID(0).toString == "B" && x.PlayerStack > 0) BasicStrategy(x)
                    else if (x.PlayerID(0).toString == "C" && x.PlayerStack > 0) CardCountingWithBasicStrategy(x)
                    else if (x.PlayerID(0).toString == "R" && x.PlayerStack > 0) RandomStrategy(x)
                    else IO(x)
                })
            PlayGameState(next_states)
        }
        else states
    }

    def run = IO( PlayGameState(InitializeGameState))
}

