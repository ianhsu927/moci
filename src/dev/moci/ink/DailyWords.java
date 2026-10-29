package dev.moci.ink;

import java.time.LocalDate;

final class DailyWords {
    private static final String[][] WORDS={
        {"serendipity","n. 意外发现美好事物的机缘"},
        {"resilient","adj. 有韧性的；能迅速恢复的"},
        {"curiosity","n. 好奇心；求知欲"},
        {"embrace","v. 拥抱；欣然接受"},
        {"subtle","adj. 微妙的；不易察觉的"},
        {"insight","n. 洞察力；深入的理解"},
        {"thrive","v. 茁壮成长；蓬勃发展"},
        {"deliberate","adj. 深思熟虑的；故意的"},
        {"perspective","n. 视角；看法"},
        {"tranquil","adj. 安静的；平和的"},
        {"versatile","adj. 多才多艺的；多用途的"},
        {"cherish","v. 珍爱；珍惜"},
        {"coherent","adj. 连贯的；条理清楚的"},
        {"endeavor","n. / v. 努力；尝试"},
        {"vivid","adj. 生动的；鲜明的"},
        {"compassion","n. 同情；怜悯"},
        {"refine","v. 改进；提炼"},
        {"fleeting","adj. 短暂的；转瞬即逝的"},
        {"intuitive","adj. 直觉的；易于理解的"},
        {"gratitude","n. 感激；感谢"},
        {"immerse","v. 使沉浸；使浸没"},
        {"eloquent","adj. 雄辩的；有说服力的"},
        {"diligent","adj. 勤奋的；认真尽责的"},
        {"nuance","n. 细微差别"},
        {"flourish","v. 繁荣；茁壮成长"},
        {"authentic","adj. 真实的；正宗的"},
        {"contemplate","v. 沉思；仔细考虑"},
        {"enduring","adj. 持久的；经久不衰的"},
        {"harmony","n. 和谐；协调"},
        {"cultivate","v. 培养；耕作"},
        {"pragmatic","adj. 务实的；讲求实效的"},
        {"wonder","n. 惊奇；奇迹"},
        {"persevere","v. 坚持不懈"},
        {"lucid","adj. 清晰易懂的；头脑清醒的"},
        {"aspire","v. 渴望；追求"},
        {"abundant","adj. 丰富的；充裕的"}
    };
    static long today(){return LocalDate.now().toEpochDay();}
    static String[][] forDay(long day){
        String[][] result=new String[5][];
        int start=(int)(Math.floorMod(day,(long)WORDS.length)*5%WORDS.length);
        for(int i=0;i<result.length;i++)result[i]=WORDS[(start+i)%WORDS.length].clone();
        return result;
    }
}
