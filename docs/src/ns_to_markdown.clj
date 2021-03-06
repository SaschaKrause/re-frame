(ns ns-to-markdown
  "This script is used to generate the API documentation for the namespace `re-frame.core`. It reads the 
   source code in that namespace, extracts the docstrings (markdown) and produces a markdown-formatted
   file suitable for providing to `mkdocs` which builds the re-frame website. 

   It is used within the GitHub Actions docs workflow at: 
   https://github.com/day8/re-frame/blob/78ca09785e2adf9eea11f1e4bff2477d193f4b46/.github/workflows/docs-workflow.yml#L15

   The page it outputs can be seen here: http://day8.github.io/re-frame/api-re-frame.core/

   Usage: clojure -m ns-to-markdown ../src/re_frame/core.cljc > api-re-frame.core.md

   Run as a script with the Clojure CLI. Expects the first arg to be a path to a ClojureScript
   namespace. Reads the single namespace found in that file using the ClojureScript analyzer,
   extracting public var metadata such as function names, arglists and docstrings. Subsequently
   writes it out as markdown to stdout suitable for piping to a markdown file."
  (:require
    [clojure.java.io :as io]
    [cljs.analyzer]
    [cljs.analyzer.api]
    [clojure.string :as string]))

(defn multimethod?
  [var]
  (= (:tag var) 'cljs.core/MultiFn))

(defn var-type
  [opts]
  (cond
    (:macro opts)           :macro
    (:protocol-symbol opts) :protocol
    (multimethod? opts)     :multimethod
    :else                   :var))

(defn remove-quote
  [arglists]
  (if (and (list? arglists) (= (first arglists) 'quote))
    (second arglists)
    arglists))

(defn unindent
  [doc]
  (->> (string/split-lines doc)
       (map #(string/replace % #"^\s{0,2}" ""))
       (string/join "\n")))

(defn read-var
  [[_ var]]
  (-> var
      (select-keys [:name :line :arglists :doc :dynamic :added :deprecated])
      (update :arglists remove-quote)
      (update :doc unindent)
      (assoc :type (var-type var))))

(defn read-publics
  [state ns-name]
  (let [vars (cljs.analyzer.api/ns-publics state ns-name)]
    (->> vars
         (remove :protocol)
         (remove :anonymous)
         (map read-var))))

(defn analyze-file
  [file]
  (let [state (cljs.analyzer.api/empty-state)]
    (binding [cljs.analyzer/*analyze-deps* false]
      (cljs.analyzer.api/no-warn
        (cljs.analyzer.api/analyze-file state file {})))
    state))

(defn read-file
  [path]
  (try
    (let [source  (io/file path)
          ns-name (:ns (cljs.analyzer.api/parse-ns source))
          state   (analyze-file source)]
      (-> (cljs.analyzer.api/find-ns state ns-name)
          (select-keys [:name :doc])
          (assoc :publics (read-publics state ns-name))))
    (catch Exception e
      (println e))))

(defn ns->markdown
  [m]
  (format "# %s\n\n" (:name m)))

(defn arglist->markdown
  [arglist]
  (if (zero? (count arglist))
    ""
    (str " " (string/join " " arglist))))

(defn arglists->markdown
  [name-without-ns arglists]
  (reduce
    (fn [markdown arglist]
      (str markdown "\n" (format "`#!clj (%s%s)`\n" name-without-ns (arglist->markdown arglist))))
    ""
    arglists))

(defn var->markdown
  [var]
  (let [name-without-ns (name (:name var))]
    (format "## %s\n%s\n\n%s\n\n"
            name-without-ns
            (arglists->markdown name-without-ns (:arglists var))
            (:doc var))))

(defn -main
  [& args]
  (let [ns-data (read-file (first args))]
    (println (str (ns->markdown ns-data)
                  (reduce str "" (map var->markdown  (:publics ns-data)))))))
