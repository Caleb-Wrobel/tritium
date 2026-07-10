package tritium.machine

import tritium.core.*

/** Setun-70 operating memory: 27 pages of 81 trytes, page indices −13…+13
  * (three trits). Pages −13…+4 are ПЗУ (ROM), +5…+13 are ОЗУ (RAM).
  * In-page addresses are −40…+40 (four trits). Immutable: writes return a
  * new Memory. Unset pages read as all zeros.
  */
final case class Memory(pages: Map[Int, Vector[Tryte]]):

  def tryte(page: Int, addr: Int): Either[Fault, Tryte] =
    read(page, addr, 1).map(_.head)

  /** Read `len` consecutive trytes, big-endian, faulting past the page edge. */
  def read(page: Int, addr: Int, len: Int): Either[Fault, Vector[Tryte]] =
    if !Memory.validPage(page) || addr < Memory.MinAddr || addr + len - 1 > Memory.MaxAddr
    then Left(Fault.PageFault)
    else
      val p = pages.getOrElse(page, Memory.BlankPage)
      Right(p.slice(addr + 40, addr + 40 + len))

  def write(page: Int, addr: Int, data: Vector[Tryte]): Either[Fault, Memory] =
    if !Memory.validPage(page) || addr < Memory.MinAddr || addr + data.length - 1 > Memory.MaxAddr
    then Left(Fault.PageFault)
    else if !Memory.isRam(page) then Left(Fault.ReadOnlyPage)
    else
      val p = pages.getOrElse(page, Memory.BlankPage)
      val updated = data.zipWithIndex.foldLeft(p) { case (acc, (t, i)) =>
        acc.updated(addr + 40 + i, t)
      }
      Right(copy(pages = pages.updated(page, updated)))

object Memory:
  val PageSize = 81
  val MinAddr = -40
  val MaxAddr = 40
  val RomPages: Range = -13 to 4
  val RamPages: Range = 5 to 13

  val BlankPage: Vector[Tryte] = Vector.fill(PageSize)(Tryte.Zero)

  def validPage(i: Int): Boolean = i >= -13 && i <= 13
  def isRam(i: Int): Boolean = RamPages.contains(i)

  val empty: Memory = Memory(Map.empty)
