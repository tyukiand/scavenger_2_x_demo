This is a small demo that demonstrates how to 
build a Scavenger 2.x application using sbt.
It contains multiple little examples.

There are two `HelloWorld` examples (a local and a distributed version).
The code of the both examples is almost identical: we define a some
data (a simple string with `ROT13`-encrypted message), and an `AtomicAlgorithm`,
namely `ROT13`. Then we decrypt the message and print it. 

# Building the examples
This example uses `sbt` as build tool.
It also requires the sbt-pack plugin.
To add the sbt-pack plugin, edit the file `~/.sbt/0.13/plugins/plugins.sbt` and 
append the line 

    addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.7.5")

to the end of file. Then run the following command (from the current directory):

    $ sbt pack

This should generate a directory `target/pack/lib`. 
This directory will contain all the transitive 
dependencies of this examples. In particular, it will contain
JARs called `akka-actor_<version>.jar`, `scavenger_<version>.jar` and
`scavenger_demo_<version>.jar`.

# Running the examples

## Local example 

Let's start with the example `scavenger_demo.LocalHelloWorld`.
The source code of the example can be found in `src/main/scala/scavenger_demo/LocalHelloWorld`.
In this example, there is only one single actor system, and all nodes 
(master, seed, worker nodes) run in the same actor system.
You should be able to run it as follows (from the current directory):

    java -cp 'target/pack/lib/*' -Dconfig.file=scavenger.conf scavenger_demo.LocalHelloWorld

The output will look like a wall of `INFO` and `DEBUG` messages. 
Right before the application terminates (it should terminate on it's own after few seconds),
it should print out "Hello, World!" in an ascii-art box.

## Distributed example

The source code can be found in `src/main/scala/scavenger_demo/DistributedHelloWorld`.
The distributed example requires multiple independent actor systems, therefore,
if you want to run the example on a single machine, you will need to specify different
ports for master node and all worker nodes.

Open 3 different consoles (in this directory), and execute the following commands:

    $ scavenger startSeed --jars 'target/pack/lib/*'
    $ scavenger startWorker --jars 'target/pack/lib/*' --port 7523
    $ scavenger startMaster --jars 'target/pack/lib/*' --port 2546 --main scavenger_demo.DistributedHelloWorld

The exact port numbers are not important, just make something up, and try not to get any collisions.
The result should be the same as in the `LocalHelloWorld`, namely the message "Hello, World!",
framed into a ascii-art box.

TODO: currently, the nodes don't react on poison pills, so you will probably have to start 
workers and seed manually by `Ctrl+C`.

# Explanation of the examples
TODO
