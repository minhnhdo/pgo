# PGo [![Build Status](https://travis-ci.org/UBC-NSS/pgo.svg?branch=master)](https://travis-ci.org/UBC-NSS/pgo)

PGo is a source to source compiler to compile
[PlusCal](http://lamport.azurewebsites.net/tla/pluscal.html)
specifications into [Go](https://golang.org/) programs.

## Purpose/motivation

[PlusCal](http://lamport.azurewebsites.net/tla/pluscal.html) is a
language for specifying/modeling concurrent systems. It was designed
to make it easier to write [TLA+](https://github.com/tlaplus). In
particular, PlusCal can be compiled into TLA+, which can be checked
against useful system properties (using the TLC model checker). For
example, [here](https://github.com/duerrfk/skp) is a repository of
PlusCal formulations of solutions to the mutual exclusion problem.

[Go](https://golang.org/) is a C based language developed by Google
for building distributed systems. It has built in support for
concurrency with channels, and goroutines, which makes it great for
developing distributed systems.

Currently there are no tools that correspond a PlusCal/TLA+ spec with
an implementation of the spec. PGo is a tool that aims to connect the
specification with the implementation by generating Go code based on a
PlusCal specification. PGo enables the translation of a verified
PlusCal specification of a distributed system algorithm into a
semantically equivalent Go program.

## Current status

Actively under development. PGo supports compilation of most
uni-process and very simple multiprocess PlusCal algorithms into
corresponding compilable and runnable Go code.

See [`manual.pdf`](https://github.com/UBC-NSS/pgo/blob/master/manual.pdf) in the
repository for a snapshot of the latest version of the manual that details
implemented features and several examples.

## Usage

To learn how to use PGo, see the [PGo usage page](https://github.com/UBC-NSS/pgo/wiki/PGo-Usage).

## How it works

PGo is a source to source compiler written in Java. It compiles specifications written in an extension of PlusCal, called Modular PlusCal (see the [Modular PlusCal page](https://github.com/UBC-NSS/pgo/wiki/Modular-PlusCal) for more details), to Go programs.

## How to install (for development)

Requirements: IntelliJ, Eclipse, or Ant 1.9

First download/clone the repository

Option 1: Import as an IntelliJ project.

Option 2: Import as an Eclipse project.

Option 3: Execute `ant build` to compile the project and then execute `pgo.sh [options] pcalfile` to compile `pcalfile`.

Dependencies:

- The [Plume options library](https://mernst.github.io/plume-lib/).

- [Java Hamcrest](http://hamcrest.org/JavaHamcrest/).

- The [JSON reference implementation](https://github.com/stleary/JSON-java).

PGo is tested using OpenJDK 1.8, and 1.9, and Go 1.8, 1.9, 1.10, and 1.11.

## How to run (for development)

Use `pgo.sh` to invoke the compiler. Below are the options that the compiler accepts.

```bash
Usage: pgo [options] spec
  --version=<boolean>          - Version [default false]
  -h --help=<boolean>          - Print usage information [default false]
  -q --logLvlQuiet=<boolean>   - Reduce printing during execution [default false]
  -v --logLvlVerbose=<boolean> - Print detailed information during execution  [default false]
  -m --mpcalCompile=<boolean>  - Compile a Modular PlusCal spec to vanilla PlusCal [default false]
  -c --configFilePath=<string> - path to the configuration file, if any
```

## Further notes for developers

If you use Eclipse, you should import the code style found in the
`pgo-code-style.epf` file by clicking `File -> Import...` and
selecting the file.

Furthermore, use the Unix text file line delimiter (especially
important if you are using Windows) by going to Eclipse's
preferences/options, and under General and Workspace set "New text
file line delimiter" to be "Unix".

By default Eclipse does not enable assertions. Our projects assume
that you have assertions enabled at all times.  To globally enable
assertions as a default for all projects, go to Window -> Preferences
-> Java / Installed JREs.  Select the JRE and click "Edit...".
