# A Scala 3 project with Typelevel libraries, Apache Kafka, Apache Spark, Apace Iceberg, Apache Superset and Trino

There are a couple of open-source tools and Scala libraries that I've been interested in. This project is designed to let me try a few of them out.

The goals of this project is the following:
1. - [x] Create a REST API that simulates the game of Blackjack. 
2. - [x] Create a set of fake players that make calls to this API to play the game, some of which are counting cards to improve their odds.
3. - [x] Send the API calls of players to some sort of event store.
4. Stream data out of the event store to try and detect card counters, as well as write events to a database.
5. Model the data in some way that it is easily accessible and useful for visualizing
6. Visualize statistics around how players are behaving and how well the house is doing.


(1-2): From Scala, I'm looking to become more comfortable with functional programming, effect systems, and the [programs-as-values](https://systemfw.org/posts/programs-as-values-I.html) 
paradigm. Between the Typelevel and Zio stacks, both seem like good options, and the choice for using Typelevel libraries here was because 
I had already downloaded a book on [Scala with Cats](https://underscore.io/books/scala-with-cats/). I am using http4s to both create and submit requests to the server.

(3): From the Apache Foundation, I wanted to get some basic experience publishing events to a Kafka topic and then consuming those events in some meaningful way. 
To produce events, I am goign to combine the fs2-kafka library with our http4s server to publish events to a 'player actions' Kafka topic. The Kafka cluster runs locally on my machine.

(4): Apache Spark is a popular tool for big data processing and comes with a couple of useful APIs such as Spark Streaming and the Spark ML library. 
I've used these in the past but haven't had a chance to play with them in a while. At my work, my team uses Snowflake (both SnowSQL and Snowpark) to process and analyze data.
The plan here is to stream data from the Kafka topic into Spark to detect card counting. At the same time, a seperate Spark consumer is going to batch load events into Iceberg tables.
I may switch to Apache Flink in the card counting detection stream, since super low-latency fraud detection seems like a use case that Flink was designed for, but Spark is more familiar to most and easier to find support for.

(5) This part will definetly happen with Spark. I'm not sure what the best way to model this data will be yet, but I imagine I would want to answer questions such as 
- How many players were active each minute?
- How much money flowed into or out of the casino each minute?
- How many unique players have we seen?
- How many players do we believe are counting cards?

I'm tempted to just leave it as one big event table for now and write some more complicated Trino queries to extract that information, but I could always set up some
batch processing jobs with spark to aggregate that information before it is needed to produce visuals.

(6) The plan currently is to use Apache Superset together with Trino to query Iceberg tables and produce some nice visuals. 
I may pivot the visualization layer to something else, like R shiny, but what matters most to me at the persistent data layer is getting some experience with Iceberg tables.

