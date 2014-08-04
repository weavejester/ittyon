# Ittyon

[![Build Status](https://travis-ci.org/weavejester/ittyon.svg?branch=master)](https://travis-ci.org/weavejester/ittyon)

An experimental library designed to manage state in games. The API is
still currently in flux.

Ittyon draws inspiration from the [entity component][1] model, but
provides a greater separation between code and data.

[1]: https://en.wikipedia.org/wiki/Entity_component_system

## Installation

Add the following to your project dependencies:

```clojure
[ittyon "0.0.11"]
```

## Overview

Ittyon is structured into __entities__, __aspects__ and __reactions__.

Entities provide identity, and by default are UUIDs chosen at
random.

Aspects provide state, by associating entities with values. For
example, an entity might have a `::position` aspect with the value
`[3 4]`.

Reactions provide the game logic, by evaluating code when certain
events occur.

## License

Copyright © 2014 James Reeves

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
