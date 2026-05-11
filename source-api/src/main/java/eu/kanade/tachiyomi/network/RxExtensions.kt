package eu.kanade.tachiyomi.network

import rx.Observable
import rx.Single
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers

fun <T> Observable<T>.subscribeOnIO(): Observable<T> = this.subscribeOn(Schedulers.io())

fun <T> Observable<T>.observeOnMain(): Observable<T> = this.observeOn(AndroidSchedulers.mainThread())

fun <T> Single<T>.subscribeOnIO(): Single<T> = this.subscribeOn(Schedulers.io())

fun <T> Single<T>.observeOnMain(): Single<T> = this.observeOn(AndroidSchedulers.mainThread())
