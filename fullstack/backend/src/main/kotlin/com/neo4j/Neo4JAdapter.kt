package com.neo4j

import com.attackAgent.TECHNIQUE_EASYNESS_MAP
import com.attackAgent.getMitreTechnique
import com.controller.Neo4JController
import org.neo4j.driver.Driver
import org.neo4j.driver.Result
import org.neo4j.driver.Session
import org.neo4j.driver.Values.parameters

class Neo4JAdapter {

  private val driver: Driver = Neo4JController.driver

  val nodes: MutableMap<Int, Node> = mutableMapOf()
  private var attackGraph: Node = buildAttackGraph() // replace with optimisedBuild()

  fun getGraph(): Node {
    return attackGraph
  }

  fun optimisedBuild(): Node {

    val session: Session = driver.session()

    val edges: List<Int> = session.writeTransaction { tx ->
      val result: Result = tx.run(
              "MATCH(x:Permission)-[:To]->(z:Rule)-[:To]->(y:Permission) CALL apoc.create.vRelationship(x, z.text, {id: z.node_id, start: x.node_id, end: y.node_id}, y) yield rel RETURN rel", parameters()
      )
      result.list { r -> r.get("start").toString().toInt() }
    }

    val permissions: String = session.writeTransaction { tx ->
      val result: Result = tx.run(
              "MATCH(n:Permission) RETURN n", parameters()
      )
      result.list().toString()
    }

    val startPermissions = session.writeTransaction { tx ->
      val result: Result = tx.run(
              "MATCH(f:Fact)-[:To]->(r:Rule)-[:To]->(p:Permission) WHERE (f.text STARTS WITH \"attackerLocated\") RETURN p", parameters()
      )
      result.list()
    }

    println(edges)

    return Node(0, "", setOf())
  }

  @Deprecated("Use optimisedBuild()")
  fun update() {
    attackGraph = buildAttackGraph()
  }

  @Deprecated("Use optimisedBuild()")
  private fun buildAttackGraph(): Node {

    val ruleNodeIds: MutableList<Int> = mutableListOf()
    for (rule: Int in connectedRule(attackerLocatedNode())) {
      ruleNodeIds.add(rule)
    }
    val connections: Set<Rule> = buildRules(ruleNodeIds)
    val node = Node(0, "start", connections)
    nodes[0] = node
    return node
  }

  @Deprecated("Use optimisedBuild()")
  /* id required to be id of a permission node */
  private fun buildNode(id: Int): Node {
    if (!nodes.containsKey(id)) {
      val permission: String = getNodeText(id)
      val connections: Set<Rule> = buildRules(connectedRules(id))
      nodes[id] = Node(id, permission, connections)
    }
    return nodes[id]!!
  }

  @Deprecated("use optimiseBuild()")
  /* ids required to be ids of rule nodes */
  private fun buildRules(ids: List<Int>): Set<Rule> {
    val rules: MutableSet<Rule> = mutableSetOf()
    for (id in ids) {
      val rule: Rule = buildRule(id)
      rules.add(rule)
    }
    return rules
  }

  @Deprecated("use optimiseBuild()")
  /* id required to be id of a rule node */
  private fun buildRule(id: Int): Rule {
    val rule: String = getNodeText(id)
    val dest: Node = buildNode(connectedPermission(id))
    return Rule(id, rule, dest)
  }

  @Deprecated("use optimiseBuild()")
  /* id required to be id of a rule node */
  private fun connectedPermission(id: Int): Int {
    val session: Session = driver.session()
    return session.writeTransaction { tx ->
      val result: Result = tx.run(
              "MATCH(start {node_id: $id})-[:To]->(end:Permission) RETURN end.node_id", parameters()
      )
      result.list()[0].get(0).toString().toInt()
    }
  }

  @Deprecated("use optimiseBuild()")
  /* id required to be id of a permission node */
  private fun connectedRules(id: Int): List<Int> {
    val session: Session = driver.session()
    return session.writeTransaction { tx ->
      val result: Result = tx.run(
              "MATCH(start {node_id: $id})-[:To]->(end:Rule) RETURN end.node_id", parameters()
      )
      result.list { r -> r.get(0).toString().toInt() }
    }
  }

  @Deprecated("use optimiseBuild()")
  /* id required to be id of a fact node */
  private fun connectedRule(id: Int): List<Int> {
    val session: Session = driver.session()
    return session.writeTransaction { tx ->
      val result: Result = tx.run(
              "MATCH(start {node_id: $id})-[:To]->(end:Rule) RETURN end.node_id", parameters()
      )
      result.list { r -> r.get(0).toString().toInt() }
    }
  }

  @Deprecated("use optimiseBuild()")
  private fun getNodeText(id: Int): String {
    val session: Session = driver.session()
    return session.writeTransaction { tx ->
      val result: Result = tx.run(
              "MATCH(n {node_id: $id}) RETURN n.text", parameters()
      )
      result.list()[0].get(0).toString().replace("\"", "")
    }
  }

  @Deprecated("use optimiseBuild()")
  private fun attackerLocatedNode(): Int {
    val session: Session = driver.session()
    return session.writeTransaction { tx ->
      val result: Result = tx.run(
              "MATCH(x) WHERE x.text STARTS WITH \"attackerLocated\" RETURN x.node_id",
              parameters()
      )
      result.list()[0].get(0).toString().toInt()
    }
  }
}

val adapter: Neo4JAdapter = Neo4JAdapter()

fun main(args: Array<String>) {

  adapter.optimisedBuild()

  for (n: Node in adapter.nodes.values) {
    println(String.format("Node: ${n.getPermission()}"))
    for (r: Rule in n.getConnections()) {
      println(String.format("    - ${r.getText()}"))
    }
  }
}

class Node(
        @Deprecated("Being removed") private val id: Int,
        private val permission: String,
        private val connections: Set<Rule>
) {
  fun getId(): Int {
    return id
  }

  fun getPermission(): String {
    return permission
  }

  fun getConnections(): Set<Rule> {
    return connections
  }
}

class Rule(
        private val id: Int,
        private val text: String,
        private val dest: Node
) {

  companion object {
    const val DEFAULT_EASINESS = Int.MAX_VALUE
  }

  var easiness: Int = DEFAULT_EASINESS

  fun getId(): Int{
    return id
  }

  fun getText(): String{
    return text
  }

  fun getDest(): Node {
    return dest
  }

  fun calculateEasinessScore() {
    easiness = calculateScore(TECHNIQUE_EASYNESS_MAP)
  }

  fun calculateScore(scoreMap: Map<String, Int>): Int {
    val technique = getMitreTechnique(this)
    return scoreMap.getOrDefault(technique.technique, 0)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Rule) return false
    if (text == other.text) return true
    return false
  }

  override fun hashCode(): Int {
    var result = text.hashCode()
    result = 31 * result + dest.hashCode()
    return result
  }

}