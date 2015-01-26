/*
 * SpinalHDL
 * Copyright (c) Dolu, All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */

package spinal

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * Created by PIC18F on 21.01.2015.
 */


object Vec{
  def apply[T <: Data](elements : Iterable[T]) : Vec[T] = {
    val vecType = elements.reduce((a,b) => {
      if (a.getClass.isAssignableFrom(b.getClass)) a
      else if (b.getClass.isAssignableFrom(a.getClass)) b
      else throw new Exception("can't mux that")
    }).clone()
    val vec = new Vec(vecType)
    vec.vec ++= elements
    vec
  }



  def tabulate[T <: Data](size : Int)(gen : (Int)=> T) : Vec[T] ={
    this((0 until size).map(gen(_)))
  }

  def fill[T <: Data](size : Int)(gen : => T) : Vec[T] ={
    tabulate(size)(_ => gen)
  }
}


object SeqMux {
  def apply[T <: Data](elements: Seq[T], address: UInt): T = {
    val addressBools = address.toBools
    def stage(elements: Seq[T], level: Int): T = {
      elements.size match {
        case 0 => throw new Exception("Can't mux a Vec of size zero")
        case 1 => elements(0)
        case _ => {
          val split = elements.grouped((elements.size + 1) / 2).toList
          Mux(addressBools(level), stage(split(1), level + 1), stage(split(0), level + 1))
        }
      }
    }
    stage(elements, 0)
  }
}

class VecAccessAssign[T <: BaseType](enables: Seq[Bool], tos: Seq[T]) extends Assignable {
  override def assignFrom(that: Data): Unit = {
    for ((enable, to) <- (enables, tos).zipped) {
      when(enable) {
        to assignFrom that
      }
    }
  }
}

class Vec[T <: Data](baseType: T) extends MultiData with collection.IndexedSeq[T]{
  //TODO protect from modification / check no read/write on vec come before last modification
  val vec = ArrayBuffer[T]()
  val accessMap = mutable.Map[UInt, T]()
  var vecTransposedCache: ArrayBuffer[ArrayBuffer[BaseType]] = null


  def vecTransposed: ArrayBuffer[ArrayBuffer[BaseType]] = {
    if (vecTransposedCache == null) {
      vecTransposedCache = new ArrayBuffer[ArrayBuffer[BaseType]]()
      val size = baseType.flatten.size
      for (i <- 0 until size)
        vecTransposedCache += ArrayBuffer[BaseType]()

      for (vecElement <- vec) {
        for (((eName, e), i) <- vecElement.flatten.zipWithIndex) {
          vecTransposedCache(i) += e;
        }
      }
    }
    vecTransposedCache
  }

  override def length: Int = vec.size


  def apply(idx: Int): T = vec(idx)

  def :=(that: Vec[T]): Unit = {
    this.assignFrom(that)
  }


  def apply(address: UInt): T = {
    access(address)
  }

  //TODO address width ?
  def access(address: UInt): T = {
    if (accessMap.contains(address)) return accessMap.get(address).getOrElse(null.asInstanceOf[T])


    val ret = SeqMux(vec, address)
    val enables = (UInt(1) << address).toBools
    for (((accessEName, accessE), to) <- (ret.flatten, vecTransposed).zipped) {
      accessE.compositeAssign = new VecAccessAssign(enables,to)
    }

    accessMap += (address -> ret)
    ret
  }

  override def assignFrom(that: Data): Unit = {
    that match {
      case that: Vec[T] => {
        if (that.vec.size != this.vec.size) throw new Exception("Can't assign Vec with a different size")
        for ((to, from) <- (this.vec, that.vec).zipped) {
          to.assignFrom(from)
        }
      }
      case _ => throw new Exception("Undefined assignement")
    }
  }


  private var elementsCache: ArrayBuffer[(String, Data)] = null

  def elements = {
    if (elementsCache == null) {
      elementsCache = ArrayBuffer[(String, Data)]()
      for (i <- 0 until vec.size)
        elementsCache += Tuple2(i.toString, vec(i))

    }
    elementsCache
  }

}