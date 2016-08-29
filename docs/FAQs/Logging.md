### Question

I use logging method X, how can I make re-frame use my method? 

## Answer

re-frame makes use of the logging functions: `warn`, `log`, `error`, `group` and `groupEnd`.  

By default, these functions map directly to the js/console equivalents, but you can 
override that by providing your own set or subset of these functions using 
`re-frame.core/set-loggers!` like this:
```clj
(defn my-warn
   [& args]      
   (my-special-warner (apply str args)))

(defn my-log
   [& args]
   (my-special-logger (apply str args)))

(re-frame.core/set-loggers!  {:warn  my-warn   
                              :log   my-log 
                              ...})
```