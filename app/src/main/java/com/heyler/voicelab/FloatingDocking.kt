package com.heyler.voicelab

internal enum class FloatingEdge { LEFT,RIGHT,TOP,BOTTOM }
internal data class FloatingPosition(val x:Int,val y:Int,val edge:FloatingEdge)
internal data class FloatingBox(val left:Int,val top:Int,val width:Int,val height:Int){
    val right get()=left+width
    val bottom get()=top+height
    fun overlaps(other:FloatingBox)=left<other.right&&right>other.left&&top<other.bottom&&bottom>other.top
}

/** Coordinates are relative to the usable display, excluding Android's bars and cutouts. */
internal object FloatingDocking {
    fun onEdge(width:Int,height:Int,size:Int,x:Int,y:Int,edge:FloatingEdge):FloatingPosition{
        val maxX=(width-size).coerceAtLeast(0);val maxY=(height-size).coerceAtLeast(0)
        return FloatingPosition(when(edge){FloatingEdge.LEFT->0;FloatingEdge.RIGHT->maxX;else->x.coerceIn(0,maxX)},when(edge){FloatingEdge.TOP->0;FloatingEdge.BOTTOM->maxY;else->y.coerceIn(0,maxY)},edge)
    }
    fun nearest(width:Int,height:Int,size:Int,x:Int,y:Int,previous:FloatingEdge):FloatingPosition{
        val px=x.coerceIn(0,(width-size).coerceAtLeast(0));val py=y.coerceIn(0,(height-size).coerceAtLeast(0))
        val distances=mapOf(FloatingEdge.LEFT to px,FloatingEdge.RIGHT to (width-size-px).coerceAtLeast(0),FloatingEdge.TOP to py,FloatingEdge.BOTTOM to (height-size-py).coerceAtLeast(0))
        val edge=distances.keys.minWith(compareBy<FloatingEdge>{distances.getValue(it)}.thenBy{if(it==previous)0 else 1})
        return onEdge(width,height,size,px,py,edge)
    }
    fun icon(position:FloatingPosition,touch:Int,visible:Int):FloatingBox{
        val inset=(touch-visible)/2
        val dx=when(position.edge){FloatingEdge.LEFT->0;FloatingEdge.RIGHT->touch-visible;else->inset}
        val dy=when(position.edge){FloatingEdge.TOP->0;FloatingEdge.BOTTOM->touch-visible;else->inset}
        return FloatingBox(position.x+dx,position.y+dy,visible,visible)
    }
    fun message(width:Int,height:Int,anchor:FloatingBox,cardWidth:Int,cardHeight:Int,gap:Int,edge:FloatingEdge):FloatingBox{
        val w=cardWidth.coerceAtMost(width);val h=cardHeight.coerceAtMost(height)
        val cx=(anchor.left+anchor.right-w)/2;val cy=(anchor.top+anchor.bottom-h)/2
        val candidates=mapOf(FloatingEdge.LEFT to FloatingBox(anchor.right+gap,cy,w,h),FloatingEdge.RIGHT to FloatingBox(anchor.left-w-gap,cy,w,h),FloatingEdge.TOP to FloatingBox(cx,anchor.bottom+gap,w,h),FloatingEdge.BOTTOM to FloatingBox(cx,anchor.top-h-gap,w,h))
        val order=listOf(edge)+FloatingEdge.entries.filter{it!=edge}
        val fitted=order.map{candidates.getValue(it).let{box->box.copy(left=box.left.coerceIn(0,(width-w).coerceAtLeast(0)),top=box.top.coerceIn(0,(height-h).coerceAtLeast(0)))}}
        return fitted.firstOrNull{!it.overlaps(anchor)}?:fitted.first()
    }
}
