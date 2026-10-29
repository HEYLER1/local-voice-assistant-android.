package com.heyler.voicelab

import org.junit.Assert.*
import org.junit.Test

class FloatingDockingTest {
    @Test fun releasesDockToTheNearestOfFourEdges(){
        val examples=listOf(5 to 300,300 to 300,150 to 4,150 to 650)
        val edges=listOf(FloatingEdge.LEFT,FloatingEdge.RIGHT,FloatingEdge.TOP,FloatingEdge.BOTTOM)
        examples.zip(edges).forEach{(point,edge)->
            assertEquals(edge,FloatingDocking.nearest(360,720,48,point.first,point.second,FloatingEdge.RIGHT).edge)
        }
    }
    @Test fun theVisibleCircleReachesEachUsableEdge(){
        FloatingEdge.entries.forEach{edge->
            val dock=FloatingDocking.onEdge(360,720,48,150,300,edge)
            val icon=FloatingDocking.icon(dock,48,32)
            assertEquals(32,icon.width);assertEquals(32,icon.height)
            when(edge){FloatingEdge.LEFT->assertEquals(0,icon.left);FloatingEdge.RIGHT->assertEquals(360,icon.right);FloatingEdge.TOP->assertEquals(0,icon.top);FloatingEdge.BOTTOM->assertEquals(720,icon.bottom)}
        }
    }
    @Test fun previewsStayInsideTheDisplayWithoutCoveringTheCircle(){
        listOf(240 to 400,360 to 720,720 to 360).forEach{(width,height)->
            FloatingEdge.entries.forEach{edge->
                listOf(0 to 0,width/2 to height/2,width to height).forEach{(x,y)->
                    val anchor=FloatingDocking.icon(FloatingDocking.onEdge(width,height,48,x,y,edge),48,32)
                    val card=FloatingDocking.message(width,height,anchor,224,88,2,edge)
                    assertTrue(card.left>=0&&card.top>=0&&card.right<=width&&card.bottom<=height)
                    assertFalse("$edge in ${width}x$height",card.overlaps(anchor))
                }
            }
        }
    }
    @Test fun ordinaryPreviewPlacementUsesOnlyATwoUnitGap(){
        FloatingEdge.entries.forEach{edge->
            val anchor=FloatingDocking.icon(FloatingDocking.onEdge(360,720,48,150,300,edge),48,32)
            val card=FloatingDocking.message(360,720,anchor,224,88,2,edge)
            when(edge){FloatingEdge.LEFT->assertEquals(2,card.left-anchor.right);FloatingEdge.RIGHT->assertEquals(2,anchor.left-card.right);FloatingEdge.TOP->assertEquals(2,card.top-anchor.bottom);FloatingEdge.BOTTOM->assertEquals(2,anchor.top-card.bottom)}
        }
    }
    @Test fun resizingKeepsTheChosenEdgeAndClampsTheOtherCoordinate(){
        val resized=FloatingDocking.onEdge(720,360,48,312,650,FloatingEdge.RIGHT)
        assertEquals(FloatingPosition(672,312,FloatingEdge.RIGHT),resized)
        assertEquals(FloatingEdge.RIGHT,FloatingDocking.nearest(360,720,48,312,0,FloatingEdge.RIGHT).edge)
    }
}
