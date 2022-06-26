import sbt.Keys.onLoadMessage

object ConsoleHelper {
  def header(text: String): String = s"${Console.CYAN}$text${Console.RESET}"

  def welcomeMessage =
    """
      |
      |    __  ___              
      |   /  |/  /_  __________ 
      |  / /|_/ / / / / ___/ _ \
      | / /  / / /_/ (__  )  __/
      |/_/  /_/\__,_/____/\___/ 
      |                         
      |
      |""".stripMargin.split("\n").map(header).mkString("\n")
}
