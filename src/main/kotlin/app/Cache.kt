package app

import java.lang.ref.ReferenceQueue
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap

interface Cache {
	val size: Int

	operator fun set(key: Any, value: Any)

	operator fun get(key: Any): Any?

	fun remove(key: Any): Any?

	fun clear()
}

class PerpetualCache : Cache {
	private val cache = ConcurrentHashMap<Any, Any>()

	override val size: Int
		get() = cache.size

	override fun set(key: Any, value: Any) {
		this.cache[key] = value
	}

	override fun remove(key: Any) = this.cache.remove(key)

	override fun get(key: Any) = this.cache[key]

	override fun clear() = this.cache.clear()
}

class SoftCache(private val delegate: Cache) : Cache {
	private val referenceQueue = ReferenceQueue<Any>()

	private class SoftEntry internal constructor(
			internal val key: Any,
			value: Any,
			referenceQueue: ReferenceQueue<Any>) : SoftReference<Any>(value, referenceQueue)

	override val size: Int
		get() = delegate.size

	override fun set(key: Any, value: Any) {
		removeUnreachableItems()
		val softEntry = SoftEntry(key, value, referenceQueue)
		delegate[key] = softEntry
	}

	override fun remove(key: Any) {
		delegate.remove(key)
		removeUnreachableItems()
	}

	override fun get(key: Any): Any? {
		val softEntry = delegate[key] as SoftEntry?
		softEntry?.get()?.let { return it }
		delegate.remove(key)
		return null
	}

	override fun clear() = delegate.clear()

	private fun removeUnreachableItems() {
		var softEntry = referenceQueue.poll() as SoftEntry?
		while (softEntry != null) {
			val key = softEntry.key
			delegate.remove(key)
			softEntry = referenceQueue.poll() as SoftEntry?
		}
	}
}