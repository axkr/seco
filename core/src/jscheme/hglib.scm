(import "org.hypergraphdb.*")
(import "org.hypergraphdb.atom.*")
(import "org.hypergraphdb.algorithms.*")
(import "org.hypergraphdb.query.*")

(define (hg:get h) (.get niche h))
(define (hg:add x) (.add niche x))
(define (hg:add-link x . target-set)
  (.add niche (HGValueLink. x (apply array (cons HGHandle.class target-set)))))

(define (hg:condition form)
  (define (empty? l) (eq? l '()))
  (define (get-path s)
      (define (internal s)
         (cond ((eq? s #null) (error "Attempt to split dotted path of a null string"))
                  ((not (.isAssignableFrom String.class (.getClass s))) (internal (.toString s)))
             (else (let ((i (.indexOf s ".")))
                (if (> i -1)
                   (cons (.substring s 0 i) (internal (.substring s (+ i 1))))
                   (cons s '())))
             )
         )
       )
      (apply array (cons  String.class (internal s)))
   )
   (cond
      ((list? form) (let ( (condition (car form)) (parameters (cdr form)) )
     (cond ( (eq? condition 'type) (AtomTypeCondition. (eval (car parameters))))
               ((eq? condition 'link) (LinkCondition. (list->array HGHandle.class (map eval parameters))))
               ((eq? condition 'subsumes) (SubsumesCondition. (eval (car parameters))))
               ((eq? condition 'subsumed) (SubsumedCondition. (eval (car parameters))))
               ((eq? condition 'and) (let ((result (And.))) (for-each (lambda (x) (.add result (hg:condition x))) parameters) result))
               ((eq? condition 'or) (let ((result (Or.))) (for-each (lambda (x) (.add result (hg:condition x))) parameters) result))
               ((eq? condition '=) (if (empty? (cdr parameters))
                                                               (AtomValueCondition. (eval (car parameters)) ComparisonOperator.EQ$)
			       (AtomPartCondition. (get-path (car parameters)) (eval (cadr parameters)) ComparisonOperator.EQ$)
			 ))
               ((eq? condition '<) (if (empty? (cdr parameters))
                                                               (AtomValueCondition. (eval (car parameters)) ComparisonOperator.LT$)
			       (AtomPartCondition. (get-path (car parameters)) (eval (cadr parameters)) ComparisonOperator.LT$)
			 ))
               ((eq? condition '>) (if (empty? (cdr parameters))
                                                               (AtomValueCondition. (eval (car parameters)) ComparisonOperator.GT$)
			       (AtomPartCondition. (get-path (car parameters)) (eval (cadr parameters)) ComparisonOperator.GT$)
			 ))
               (else (error "Expected HyperGraph query condition, but got: " condition)))))
     (else (error "Don't know how to convert to HyperGraph condition: " form))))

(define  (hg:label-link label . outset)
   (let ((outA (list->array HGHandle.class outset)))
      (.add niche (HGValueLink. label outA))))

(define-macro (hg:find condition) `(.find niche (hg:condition (quote ,condition))))

(define-macro (hg:find-unique condition) 
   `(let ((rs  (hg:find ,condition))) (if (.hasNext rs) (.next rs) #null)))

(define-macro (hg:with-rs rs condition actions) 
  `(let ((,rs (hg:find ,condition)))
        (tryCatch (let ((result (begin ,actions))) (.close ,rs) result)  (lambda (e) (.close ,rs) (throw e))))))
        
(define (hg:rs->list rs)
  (cond 
    ((eq? #null rs) #null)
    ((.hasNext rs) (cons (.next rs) (hg:rs->list rs)))
    (else '())))

(define (hg:rs->array class rs)
  (list->array class (hg:rs->list rs)))
