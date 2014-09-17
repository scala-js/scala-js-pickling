package org.scalajs.spickling

import scala.reflect.ClassTag
import scala.collection.mutable
import scala.collection.immutable.Map.{Map1, Map2, Map3, Map4}
import scala.collection.immutable.HashMap.HashTrieMap
import scala.collection.immutable.Set.{Set1, Set2, Set3, Set4}
import scala.collection.immutable.HashSet.HashTrieSet

object PicklerRegistry extends BasePicklerRegistry {
  class SingletonFullName[A](val name: String)

  object SingletonFullName extends PicklerMaterializers
}

trait PicklerRegistry {
  def pickle[P](value: Any)(implicit builder: PBuilder[P],
      registry: PicklerRegistry = this): P
  def unpickle[P](pickle: P)(implicit reader: PReader[P],
      registry: PicklerRegistry = this): Any
}

class BasePicklerRegistry extends PicklerRegistry {
  import PicklerRegistry._

  private val picklers = new mutable.HashMap[String, Pickler[_]]
  private val unpicklers = new mutable.HashMap[String, Unpickler[_]]
  private val singletons = new mutable.HashMap[Any, String]
  private val singletonsRev = new mutable.HashMap[String, Any]

  registerBuiltinPicklers()

  private def registerInternal(clazz: Class[_], pickler: Pickler[_],
      unpickler: Unpickler[_]): Unit = {
    picklers(clazz.getName) = pickler
    unpicklers(clazz.getName) = unpickler
  }

  def register[A : ClassTag](pickler: Pickler[A],
      unpickler: Unpickler[A]): Unit = {
    registerInternal(implicitly[ClassTag[A]].runtimeClass, pickler, unpickler)
  }

  def register[A : ClassTag](implicit pickler: Pickler[A],
      unpickler: Unpickler[A]): Unit = {
    register(pickler, unpickler)
  }

  def register[A <: Singleton](obj: A)(implicit name: SingletonFullName[A]): Unit = {
    singletons(obj) = name.name
    singletonsRev(name.name) = obj
  }

  def pickle[P](value: Any)(implicit builder: PBuilder[P],
      registry: PicklerRegistry): P = {
    if (value == null) {
      builder.makeNull()
    } else {
      singletons.get(value) match {
        case Some(name) => builder.makeObject(("s", builder.makeString(name)))
        case _ =>
          val className = value.getClass.getName
          val pickler = picklers(className)
          val pickledValue = pickler.pickle[P](value.asInstanceOf[pickler.Picklee])
          builder.makeObject(
              ("t", builder.makeString(className)),
              ("v", pickledValue))
      }
    }
  }

  def unpickle[P](pickle: P)(implicit reader: PReader[P],
      registry: PicklerRegistry): Any = {
    if (reader.isNull(pickle)) {
      null
    } else {
      val s = reader.readObjectField(pickle, "s")
      if (!reader.isUndefined(s)) {
        singletonsRev(reader.readString(s))
      } else {
        val className = reader.readString(reader.readObjectField(pickle, "t"))
        val unpickler = unpicklers(className)
        unpickler.unpickle[P](reader.readObjectField(pickle, "v"))
      }
    }
  }

  private def registerBuiltinPicklers(): Unit = {
    registerPrimitive[Boolean, java.lang.Boolean]
    registerPrimitive[Char, java.lang.Character]
    registerPrimitive[Byte, java.lang.Byte]
    registerPrimitive[Short, java.lang.Short]
    registerPrimitive[Int, java.lang.Integer]
    registerPrimitive[Long, java.lang.Long]
    registerPrimitive[Float, java.lang.Float]
    registerPrimitive[Double, java.lang.Double]

    register[String]

    // java date
    register[java.util.Date]
    // TODO: fix error
    // option
    //register(None)
    //register[Some[Any]]
    // list
    //register[::[Any]]
    //register(Nil)
    // map
    registerInternal((Map[Nothing, Nothing]()).getClass, Pickler.MapPickler, Unpickler.MapUnpickler)
    register[Map[Any, Any]]
    register[Map1[Any, Any]]
    register[Map2[Any, Any]]
    register[Map3[Any, Any]]
    register[Map4[Any, Any]]
    register[HashTrieMap[Any, Any]]
    registerInternal((Set[Nothing]()).getClass, Pickler.SetPickler, Unpickler.SetUnpickler)
    register[Set[Any]]
    register[Set1[Any]]
    register[Set2[Any]]
    register[Set3[Any]]
    register[Set4[Any]]
    register[HashTrieSet[Any]]
  }

  private def registerPrimitive[P : ClassTag, W : ClassTag](
      implicit pickler: Pickler[P], unpickler: Unpickler[P]): Unit = {
    register[P]
    registerInternal(implicitly[ClassTag[W]].runtimeClass, pickler, unpickler)
  }
}
    // TODO: fix error
