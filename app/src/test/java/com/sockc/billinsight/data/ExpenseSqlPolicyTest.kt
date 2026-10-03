package com.sockc.billinsight.data

import org.junit.Assert.*
import org.junit.Test

class ExpenseSqlPolicyTest {
    @Test fun predicatesDoNotContainLeadingConjunctions() {
        val predicates=ExpenseSqlPolicy.merchantConditions()
        assertEquals(4,predicates.size)
        assertTrue(predicates.all { !it.trimStart().startsWith("AND ", ignoreCase=true) })
        assertTrue(predicates.all { !it.trimStart().startsWith("OR ", ignoreCase=true) })
        val where=predicates.joinToString(" AND ")
        assertTrue(where.contains("AND NOT EXISTS"))
        assertFalse(Regex("\\bAND\\s+AND\\b",RegexOption.IGNORE_CASE).containsMatchIn(where))
    }

    @Test fun optionalFiltersStillComposeWithoutDoubleAnd() {
        val conditions=ExpenseSqlPolicy.merchantConditions()
        conditions += "platform=?"
        conditions += "category=?"
        val where=conditions.joinToString(" AND ")
        assertTrue(where.contains("AND platform=? AND category=?"))
        assertFalse(Regex("\\bAND\\s+AND\\b",RegexOption.IGNORE_CASE).containsMatchIn(where))
    }
}
