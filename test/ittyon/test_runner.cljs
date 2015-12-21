(ns ittyon.test-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            ittyon.client-server-test
            ittyon.core-test))

(doo-tests 'ittyon.client-server-test
           'ittyon.core-test)
