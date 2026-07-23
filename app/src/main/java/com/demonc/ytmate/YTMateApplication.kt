package com.demonc.ytmate

import android.app.Application
import androidx.multidex.MultiDex

/**
 * Application customizada que garante que todas as classes secundárias dos
 * arquivos .dex sejam carregadas já na inicialização do processo.
 *
 * Sem isso, em apps com muitas dependências (Compose + OkHttp + Gson + etc.),
 * a MainActivity pode acabar em classes4.dex e o classloader padrão não a
 * encontra quando o Android tenta instanciá-la — causando:
 *
 *   java.lang.ClassNotFoundException: Didn't find class
 *   "com.demonc.ytmate.MainActivity"
 *
 * O MultiDex.install() registra os DEX secundários no PathClassLoader antes
 * de qualquer classe do app ser tocada.
 */
class YTMateApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MultiDex.install(this)
    }
}
