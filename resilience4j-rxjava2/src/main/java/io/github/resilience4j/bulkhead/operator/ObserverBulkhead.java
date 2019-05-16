/*
 * Copyright 2019 Robert Winkler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.resilience4j.bulkhead.operator;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.internal.disposables.EmptyDisposable;

import static java.util.Objects.requireNonNull;

class ObserverBulkhead<T> extends Observable<T> {

    private final Observable<T> upstream;
    private final Bulkhead bulkhead;

    ObserverBulkhead(Observable<T> upstream, Bulkhead bulkhead) {
        this.upstream = upstream;
        this.bulkhead = bulkhead;
    }

    @Override
    protected void subscribeActual(Observer<? super T> downstream) {
        if(bulkhead.tryAcquirePermission()){
            upstream.subscribe(new BulkheadObserver(downstream));
        }else{
            downstream.onSubscribe(EmptyDisposable.INSTANCE);
            downstream.onError(new BulkheadFullException(bulkhead));
        }
    }
    class BulkheadObserver extends BaseBulkheadObserver implements Observer<T> {

        private final Observer<? super T> downstreamObserver;

        BulkheadObserver(Observer<? super T> downstreamObserver) {
            super(bulkhead);
            this.downstreamObserver = requireNonNull(downstreamObserver);
        }

        @Override
        protected void hookOnSubscribe() {
            downstreamObserver.onSubscribe(this);
        }

        @Override
        public void onNext(T item) {
            whenNotDisposed(() -> downstreamObserver.onNext(item));
        }

        @Override
        public void onError(Throwable e) {
            whenNotCompleted(() -> {
                super.onError(e);
                downstreamObserver.onError(e);
            });
        }

        @Override
        public void onComplete() {
            whenNotCompleted(() -> {
                super.onSuccess();
                downstreamObserver.onComplete();
            });
        }
    }

}