package utils

trait Parallel[M[_]] {
  def parTraverse[A, B](ta: Seq[A])(f: A => M[B]): M[Seq[B]]
}