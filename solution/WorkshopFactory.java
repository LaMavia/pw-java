/*
 * University of Warsaw
 * Concurrent Programming Course 2022/2023
 * Java Assignment
 *
 * Author: Konrad Iwanicki (iwanicki@mimuw.edu.pl)
 */
package cp2022.solution;

import java.util.Collection;

import cp2022.base.Workplace;
import cp2022.base.Workshop;


public final class WorkshopFactory {
    private final static class OrderlyWorkshop {
        /*
         * enter(id) ::
         *  P(mutex)
         *  if (ws.contains(id)) { // occupied
         *   qs.get(id)[0] += 1
         *   V(mutex)
         *   P(qs.get(id)[1])
         *  } // inheriting the section
         *  ws.insert(id, thread_id)
         *  sw.insert(thread_id, id)
         *  V(mutex)
         */

        /*
         * leave() ::
         *  P(mutex)
         *  id := sw.get(thread_id)
         *  ws.remove(id)
         *  sw.remove(thread_id)
         *  q := qs.get(id)
         *  if (q[0] > 0) { q[0] -= 1; V(q[1]) }
         *  else { V(mutex) }
         */
    }

    public final static Workshop newWorkshop(
            Collection<Workplace> workplaces
    ) {
        // FIXME: implement
        // throw new RuntimeException("not implemented");
        return new cp2022.solution.OrderlyWorkshop(workplaces);
    }
    
}
