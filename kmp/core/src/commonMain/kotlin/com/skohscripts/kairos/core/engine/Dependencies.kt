package com.skohscripts.kairos.core.engine

/**
 * Dépendances « bloquée par » (docs/spec/ordonnancement.md § Dépendances). Pur.
 *
 * Arête = `(bloquée, bloquante)`. Les cycles sont neutralisés (arêtes ignorées),
 * jamais de boucle infinie ni de blocage mutuel.
 */
object Dependencies {
    data class Edge(val blocked: Long, val blocker: Long)

    /** Nœuds impliqués dans un cycle (algorithme de Kahn). */
    fun cycleNodes(edges: List<Edge>): Set<Long> {
        val nodes = LinkedHashSet<Long>()
        edges.forEach { nodes += it.blocked; nodes += it.blocker }
        val indeg = nodes.associateWith { 0 }.toMutableMap()
        val adj = nodes.associateWith { mutableListOf<Long>() }
        for (e in edges) {
            adj.getValue(e.blocker) += e.blocked
            indeg[e.blocked] = indeg.getValue(e.blocked) + 1
        }
        val queue = ArrayDeque(nodes.filter { indeg.getValue(it) == 0 })
        while (queue.isNotEmpty()) {
            val node = queue.removeLast()
            for (next in adj.getValue(node)) {
                indeg[next] = indeg.getValue(next) - 1
                if (indeg.getValue(next) == 0) queue.addLast(next)
            }
        }
        return nodes.filter { indeg.getValue(it) > 0 }.toSet()
    }

    /** Arêtes hors cycle, dédupliquées, sans auto-boucle. */
    fun acyclicEdges(edges: List<Edge>): List<Edge> {
        val cycles = cycleNodes(edges)
        val seen = HashSet<Edge>()
        return edges.filter { e ->
            e.blocked != e.blocker && !(e.blocked in cycles && e.blocker in cycles) && seen.add(e)
        }
    }

    /**
     * Tâches bloquées : au moins un bloqueur direct encore à faire (`todo`). La
     * transitivité est naturelle (un bloqueur bloqué reste `todo`). Une tâche
     * déjà faite ou archivée n'est jamais bloquée. Statut inconnu = `todo`.
     */
    /**
     * Sous-tâches à faire d'une tâche bloquée, de proche en proche : [tâche → son ancêtre bloqué le plus proche]. Une
     * sous-tâche ne se planifie pas tant que sa mère attend un bloqueur (docs/spec/dependances.md). [blocked] :
     * [blockedTaskIds] ; [parentOf] : mère de chaque tâche à faire qui en a une.
     */
    fun inheritedBlock(blocked: Set<Long>, parentOf: Map<Long, Long>): Map<Long, Long> {
        val out = LinkedHashMap<Long, Long>()
        fun nearestBlockedAncestor(id: Long): Long? {
            var p = parentOf[id]
            var steps = 0
            while (p != null && steps++ < parentOf.size + 1) {
                if (p in blocked || p in out) return if (p in blocked) p else out.getValue(p)
                p = parentOf[p]
            }
            return null
        }
        // Ordre stable : les mères avant leurs filles n'est pas garanti, on itère jusqu'au point fixe.
        var changed = true
        while (changed) {
            changed = false
            for (id in parentOf.keys) {
                if (id in blocked || id in out) continue
                nearestBlockedAncestor(id)?.let { out[id] = it; changed = true }
            }
        }
        return out
    }

    fun blockedTaskIds(edges: List<Edge>, isTodo: (Long) -> Boolean): Set<Long> =
        acyclicEdges(edges).filter { isTodo(it.blocked) && isTodo(it.blocker) }.map { it.blocked }.toSet()

    /** Pour chaque tâche bloquée, les identifiants de ses bloqueurs **directs** encore à faire. */
    fun openBlockers(edges: List<Edge>, isTodo: (Long) -> Boolean): Map<Long, List<Long>> {
        val out = LinkedHashMap<Long, MutableList<Long>>()
        for (e in acyclicEdges(edges)) if (isTodo(e.blocker)) out.getOrPut(e.blocked) { mutableListOf() } += e.blocker
        return out
    }

    /** Vrai si ajouter `(newBlocked, newBlocker)` créerait un cycle (auto-arête comprise). */
    fun wouldCreateCycle(existing: List<Edge>, newBlocked: Long, newBlocker: Long): Boolean {
        if (newBlocked == newBlocker) return true
        return newBlocked in reachableBlockers(existing, newBlocker)
    }

    private fun reachableBlockers(edges: List<Edge>, start: Long): Set<Long> {
        val blockersOf = HashMap<Long, MutableList<Long>>()
        edges.forEach { blockersOf.getOrPut(it.blocked) { mutableListOf() } += it.blocker }
        val seen = HashSet<Long>()
        val stack = ArrayDeque(blockersOf[start].orEmpty())
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (!seen.add(node)) continue
            stack.addAll(blockersOf[node].orEmpty())
        }
        return seen
    }

    /**
     * Urgence effective : un bloqueur hérite de la clé la plus petite (la plus
     * urgente) de ce qu'il bloque, transitivement (point fixe, cycles exclus).
     * Ne modifie aucune tâche.
     */
    fun <K : Comparable<K>> derivedUrgency(edges: List<Edge>, own: Map<Long, K>): Map<Long, K> {
        val active = acyclicEdges(edges)
        val effective = own.toMutableMap()
        repeat(own.size + 1) {
            var changed = false
            for (e in active) {
                val blockedKey = effective[e.blocked] ?: continue
                val blockerKey = effective[e.blocker] ?: continue
                if (blockedKey < blockerKey) {
                    effective[e.blocker] = blockedKey
                    changed = true
                }
            }
            if (!changed) return effective
        }
        return effective
    }
}
